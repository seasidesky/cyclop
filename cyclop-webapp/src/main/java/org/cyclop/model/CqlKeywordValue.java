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
package org.cyclop.model;

import net.jcip.annotations.Immutable;

/** @author Maciej Miklas */
@Immutable
public final class CqlKeywordValue extends CqlKeyword {

	public static enum Def {
		CLASS("class"), SIMPLE_STRATEGY("simplestrategy"), REPLICATION_FACTOR("replication_factor"), NETWORK_TOPOLOGY_STRATEGY(
				"networktopologystrategy"), DURABLE_WRITES("durable_writes"), TRUE("true"), FALSE("false"), OLD_NETWORK_TOPOLOGY_STRATEGY(
				"OldNetworkTopologyStrategy");

		private Def(String value) {
			this.value = new CqlKeywordValue(value.toLowerCase());
		}

		private Def(String value, CassandraVersion validFrom, CassandraVersion validTo) {
			this.value = new CqlKeywordValue(value.toLowerCase(), validFrom, validTo);
		}

		public CqlKeywordValue value;
	}

	protected CqlKeywordValue(String val) {
		super(val);
	}

	protected CqlKeywordValue(String val, CassandraVersion validFrom, CassandraVersion validTo) {
		super(val, validFrom, validTo);
	}

	@Override
	public String toString() {
		return "CqlKeywordValue{" + "part='" + part + '\'' + '}';
	}

	@Override
	public CqlType type() {
		return CqlType.KEYWORD_VALUE;
	}

	@edu.umd.cs.findbugs.annotations.SuppressWarnings("EQ_CHECK_FOR_OPERAND_NOT_COMPATIBLE_WITH_THIS")
	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		CqlPart cqlObj = (CqlPart) obj;
		return java.util.Objects.equals(partLc, cqlObj.partLc);
	}

	@Override
	public int hashCode() {
		return partLc.hashCode();
	}
}
