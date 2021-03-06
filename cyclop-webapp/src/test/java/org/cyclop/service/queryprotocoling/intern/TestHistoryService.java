/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cyclop.service.queryprotocoling.intern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import org.cyclop.model.CqlQuery;
import org.cyclop.model.CqlQueryType;
import org.cyclop.model.QueryEntry;
import org.cyclop.model.QueryHistory;
import org.cyclop.model.UserIdentifier;
import org.cyclop.model.exception.BeanValidationException;
import org.cyclop.service.common.FileStorage;
import org.cyclop.test.AbstractTestCase;
import org.cyclop.test.ThreadTestScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

/** @author Maciej Miklas */
public class TestHistoryService extends AbstractTestCase {

	public static String CR = System.getProperty("line.separator");

	@Inject
	private HistoryServiceImpl historyService;

	@Inject
	private AsyncFileStore<QueryHistory> asyncFileStore;

	private UserIdentifier user;

	@Inject
	private FileStorage storage;

	@Inject
	private ThreadTestScope threadTestScope;

	@After
	public void cleanUp() throws Exception {
		super.cleanUp();
		threadTestScope.setSingleThread(false);
	}

	@Before
	public void setup() throws Exception {
		super.setup();
		asyncFileStore.flush();
		QueryHistory history = historyService.read();
		assertNotNull(history);
		history.clear();

		assertEquals(0, history.size());

		user = historyService.getUser();
		assertNotNull(user);
		assertNotNull(user.id);
	}

	@Test
	public void testCreateReadAndClear() throws Exception {
		QueryHistory history = historyService.read();

		for (int i = 0; i < 600; i++) {
			historyService.addAndStore(new QueryEntry(new CqlQuery(CqlQueryType.SELECT, "select * " + CR
					+ "from HistoryTest where " + CR + "id=" + i), 1000 + i));
			QueryHistory historyQueue = asyncFileStore.getFromWriteQueue(user).get();
			assertNotNull(historyQueue);

			// should be the same instance
			assertSame(history, historyQueue);
		}
		assertEquals(500, history.size());

		asyncFileStore.flush();
		assertFalse(asyncFileStore.getFromWriteQueue(user).isPresent());

		assertSame(history, historyService.read());

		QueryHistory readHist = storage.read(user, QueryHistory.class).get();
		assertNotSame(history, readHist);

		for (int i = 100; i < 600; i++) {
			QueryEntry tofind = new QueryEntry(new CqlQuery(CqlQueryType.SELECT, "select * from HistoryTest where id="
					+ i), 2000 + i);
			assertTrue(tofind + " NOT FOUND IN: " + readHist, readHist.contains(tofind));

			ImmutableList<QueryEntry> readList = readHist.copyAsList();
			int index = readList.indexOf(tofind);
			assertTrue(index >= 0);
			QueryEntry read = readList.get(index);
			assertNotNull(read.executedOnUtc);
			assertEquals(1000 + i, read.runTime);
		}

		{
			history.clear();
			assertEquals(0, history.size());
			historyService.store(history);
			asyncFileStore.flush();
			assertEquals(0, storage.read(user, QueryHistory.class).get().size());
		}
	}

	@Test(expected = BeanValidationException.class)
	public void testAddAndStore_NullParams() {
		historyService.addAndStore(null);
	}

	@Test(expected = BeanValidationException.class)
	public void testAddAndStore_InvalidParams() {
		historyService.addAndStore(new QueryEntry(new CqlQuery(null, null), 1));
	}

	@Test(expected = BeanValidationException.class)
	public void testStore_InvalidParams() {
		historyService.store(null);
	}

	@Test
	public void testMultiThreadForMultipleUsers() throws Exception {
		threadTestScope.setSingleThread(false);

		Set<QueryHistory> histories = executeMultiThreadTest(300);
		assertEquals(3, histories.size());
	}

	@Test
	public void testMultiThreadForSingleUsers() throws Exception {
		threadTestScope.setSingleThread(true);

		Set<QueryHistory> histories = executeMultiThreadTest(100);
		assertEquals(1, histories.size());
	}

	public Set<QueryHistory> executeMultiThreadTest(final int repeatInTest) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(3);
		final Set<QueryHistory> histories = Collections.synchronizedSet(new HashSet<QueryHistory>());

		List<Callable<Void>> tasks = new ArrayList<>(3);
		final AtomicInteger executedCount = new AtomicInteger(0);
		for (int i = 0; i < 3; i++) {
			tasks.add(new Callable<Void>() {

				@Override
				public Void call() throws Exception {
					for (int i = 0; i < repeatInTest; i++) {
						QueryHistory history = historyService.read();
						histories.add(history);

						QueryEntry histEntry = new QueryEntry(new CqlQuery(CqlQueryType.SELECT,
								"select * from MyTable2 where id=" + UUID.randomUUID()), 4000 + i);
						history.add(histEntry);

						verifyHistEntry(history, histEntry);

						historyService.store(history);
						if (i % 20 == 0) {
							asyncFileStore.flush();
						}

						QueryHistory readHist = historyService.read();
						verifyHistEntry(readHist, histEntry);

						executedCount.incrementAndGet();
						assertEquals(0, storage.getLockRetryCount());
					}
					return null;
				}

				void verifyHistEntry(QueryHistory history, QueryEntry histEntry) {
					assertNotNull(history);

					assertTrue("History (" + executedCount + "):" + histEntry + " not found in: " + history,
							history.contains(histEntry));
				}
			});
		}

		List<Future<Void>> results = executor.invokeAll(tasks);
		executor.shutdown();
		executor.awaitTermination(5, TimeUnit.MINUTES);

		for (Future<Void> result : results) {
			result.get();
		}
		assertEquals(3 * repeatInTest, executedCount.get());
		return histories;
	}

}
/*

*/