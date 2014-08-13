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
package org.cyclop.web.panels.queryeditor;

import javax.inject.Inject;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.authroles.authorization.strategies.role.Roles;
import org.apache.wicket.authroles.authorization.strategies.role.annotations.AuthorizeInstantiation;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.string.StringValue;
import org.cyclop.model.ContextCqlCompletion;
import org.cyclop.model.CqlQuery;
import org.cyclop.model.CqlQueryResult;
import org.cyclop.model.UserPreferences;
import org.cyclop.service.cassandra.QueryService;
import org.cyclop.service.exporter.CsvQueryResultExporter;
import org.cyclop.service.um.UserManager;
import org.cyclop.web.panels.queryeditor.buttons.ButtonListener;
import org.cyclop.web.panels.queryeditor.buttons.ButtonsPanel;
import org.cyclop.web.panels.queryeditor.completionhint.CompletionHintPanel;
import org.cyclop.web.panels.queryeditor.cqlhelp.CqlHelpPanel;
import org.cyclop.web.panels.queryeditor.editor.CompletionChangeListener;
import org.cyclop.web.panels.queryeditor.editor.EditorPanel;
import org.cyclop.web.panels.queryeditor.export.QueryResultExport;
import org.cyclop.web.panels.queryeditor.horizontalresult.QueryResultHorizontalPanel;
import org.cyclop.web.panels.queryeditor.verticalresult.QueryResultVerticalPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author Maciej Miklas */
@AuthorizeInstantiation(Roles.ADMIN)
public class QueryEditorPanel extends Panel {

    private final static Logger LOG = LoggerFactory.getLogger(QueryEditorPanel.class);

    private final CqlHelpPanel cqlHelpPanel;

    private final CompletionHintPanel cqlCompletionHintPanel;

    private boolean queryRunning = false;

    private CqlQuery lastQuery;

    private final QueryResultExport queryResultExport;

    @Inject
    private CsvQueryResultExporter exporter;

    @Inject
    private UserManager userManager;

    @Inject
    private QueryService queryService;

    private final IModel<CqlQueryResult> queryResultModel;

    private final Label queryErrorLabel;
    private QueryResultPanel queryResultPanel;
    private final WebMarkupContainer queryResultContainer;

    public QueryEditorPanel(String id, PageParameters params) {
	super(id);
	setRenderBodyOnly(true);
	queryResultModel = Model.of(CqlQueryResult.EMPTY);

	cqlHelpPanel = new CqlHelpPanel("cqlHelp");
	add(cqlHelpPanel);

	UserPreferences preferences = userManager.readPreferences();
	cqlCompletionHintPanel = new CompletionHintPanel("cqlInfoHint", "Completion Hint");
	cqlCompletionHintPanel.setVisible(preferences.isShowCqlCompletionHint());
	add(cqlCompletionHintPanel);

	queryResultContainer = new WebMarkupContainer("queryResultContainer");
	queryResultContainer.setOutputMarkupPlaceholderTag(true);
	add(queryResultContainer);

	initQueryResultPanel(preferences.getResultOrientation());

	EditorPanel queryEditorPanel = initQueryEditorPanel(params);

	initButtons(queryEditorPanel, preferences);

	queryResultExport = new QueryResultExport(this, exporter);

	queryErrorLabel = initQueryErrorLabel();
    }

    private Label initQueryErrorLabel() {
	Label queryErrorLabel = new Label("queryError", Model.of(""));
	add(queryErrorLabel);
	queryErrorLabel.setVisible(false);
	queryErrorLabel.setOutputMarkupPlaceholderTag(true);
	return queryErrorLabel;
    }

    private void initQueryResultPanel(int orientation) {
	queryResultPanel = orientation == 1 ? new QueryResultHorizontalPanel(
		"queryResultPanel",
		queryResultModel) : new QueryResultVerticalPanel("queryResultPanel", queryResultModel);

	queryResultContainer.add(queryResultPanel);
    }

    private void replaceQueryResultPanel(AjaxRequestTarget target, int orientation) {
	queryResultContainer.remove(queryResultPanel);

	queryResultPanel = queryResultPanel
		.createFromTemplate(orientation == 1 ? QueryResultHorizontalPanel.class
			: QueryResultVerticalPanel.class);
	queryResultContainer.add(queryResultPanel);
	target.add(queryResultContainer);
    }

    private EditorPanel initQueryEditorPanel(PageParameters params) {

	StringValue editorContentVal = params.get("cql");
	String editorContent = editorContentVal == null ? null : editorContentVal.toString();

	EditorPanel queryEditorPanel = new EditorPanel("queryEditorPanel", editorContent);
	add(queryEditorPanel);
	queryEditorPanel.setOutputMarkupPlaceholderTag(true);

	queryEditorPanel.registerCompletionChangeListener(new CompletionChangeHelp());
	queryEditorPanel.registerCompletionChangeListener(new CompletionChangeHint());
	return queryEditorPanel;
    }

    private ButtonsPanel initButtons(final EditorPanel editorPanel, UserPreferences preferences) {
	ButtonListener buttonListener = new ButtonListener() {

	    @Override
	    public void onClickQueryResultExport(AjaxRequestTarget target) {
		queryResultExport.initiateDownload(target, lastQuery);
	    }

	    @Override
	    public void onClickExecCql(AjaxRequestTarget target) {
		handleExecQuery(target, editorPanel);
	    }

	    @Override
	    public void onClickCompletion(AjaxRequestTarget target, boolean pressed) {
		cqlCompletionHintPanel.setVisible(pressed);
		target.add(cqlCompletionHintPanel);
	    }

	    @Override
	    public void onClickResultOrientation(AjaxRequestTarget target, int orientation) {
		replaceQueryResultPanel(target, orientation);
	    }
	};

	ButtonsPanel buttonsPanel = new ButtonsPanel(
		"buttons",
		buttonListener,
		preferences.isShowCqlCompletionHint());
	add(buttonsPanel);
	return buttonsPanel;
    }

    private void handleExecQuery(AjaxRequestTarget target, EditorPanel editorPanel) {

	// this cannot happen, because java script disables execute
	// button - it's DOS prevention
	if (queryRunning) {
	    LOG.warn("Query still running - cannot execute second one");
	    return;
	}

	CqlQuery query = editorPanel.getEditorContent();

	if (query == null) {
	    return;
	}
	queryRunning = true;
	try {
	    CqlQueryResult queryResult = queryService.execute(query);
	    lastQuery = query;
	    queryResultModel.setObject(queryResult);
	    queryResultPanel.modelChanged();
	    queryErrorLabel.setVisible(false);
	    queryResultContainer.setVisible(true);
	}
	catch (Exception e) {
	    queryErrorLabel.setVisible(true);
	    queryResultContainer.setVisible(false);
	    queryErrorLabel.setDefaultModelObject(e.getMessage());
	}
	finally {
	    queryRunning = false;
	}
	editorPanel.resetCompletion();

	target.add(queryErrorLabel);
	target.add(queryResultContainer);
    }

    private final class CompletionChangeHelp implements CompletionChangeListener {

	@Override
	public void onCompletionChange(ContextCqlCompletion currentCompletion) {
	    cqlHelpPanel.changeCompletion(currentCompletion);
	}

	@Override
	public Component getReferencesForRefresh() {
	    return cqlHelpPanel;
	}
    }

    private final class CompletionChangeHint implements CompletionChangeListener {

	@Override
	public void onCompletionChange(ContextCqlCompletion currentCompletion) {
	    cqlCompletionHintPanel.changeCompletion(currentCompletion);
	}

	@Override
	public Component getReferencesForRefresh() {
	    return cqlCompletionHintPanel;
	}
    }

}
