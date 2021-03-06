/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.workbench.ht.client.editors.taskslist;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.enterprise.event.Event;

import com.google.gwt.view.client.Range;
import org.dashbuilder.common.client.error.ClientRuntimeError;
import org.dashbuilder.dataset.DataSet;
import org.dashbuilder.dataset.DataSetLookup;
import org.dashbuilder.dataset.DataSetOp;
import org.dashbuilder.dataset.client.DataSetReadyCallback;
import org.dashbuilder.dataset.filter.ColumnFilter;
import org.dashbuilder.dataset.filter.DataSetFilter;
import org.jboss.errai.security.shared.api.identity.User;
import org.jboss.errai.ui.client.local.spi.TranslationService;
import org.jbpm.workbench.common.client.PerspectiveIds;
import org.jbpm.workbench.common.client.filters.active.ActiveFilterItem;
import org.jbpm.workbench.common.client.filters.basic.BasicFilterAddEvent;
import org.jbpm.workbench.common.client.filters.basic.BasicFilterRemoveEvent;
import org.jbpm.workbench.common.client.list.ListTable;
import org.jbpm.workbench.df.client.filter.FilterSettings;
import org.jbpm.workbench.df.client.list.DataSetQueryHelper;
import org.jbpm.workbench.ht.client.resources.i18n.Constants;
import org.jbpm.workbench.ht.model.TaskSummary;
import org.jbpm.workbench.ht.model.events.TaskCompletedEvent;
import org.jbpm.workbench.ht.model.events.TaskSelectionEvent;
import org.jbpm.workbench.ht.service.TaskService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.uberfire.client.mvp.PerspectiveActivity;
import org.uberfire.client.mvp.PerspectiveManager;
import org.uberfire.client.mvp.PlaceManager;
import org.uberfire.client.workbench.events.BeforeClosePlaceEvent;
import org.uberfire.ext.widgets.common.client.breadcrumbs.UberfireBreadcrumbs;
import org.uberfire.mocks.CallerMock;
import org.uberfire.mocks.EventSourceMock;
import org.uberfire.mvp.impl.DefaultPlaceRequest;

import static org.dashbuilder.dataset.filter.FilterFactory.equalsTo;
import static org.dashbuilder.dataset.filter.FilterFactory.likeTo;
import static org.jbpm.workbench.ht.client.util.TaskUtils.TaskType;
import static org.jbpm.workbench.ht.client.util.TaskUtils.getStatusByType;
import static org.jbpm.workbench.ht.model.TaskDataSetConstants.*;
import static org.jbpm.workbench.ht.util.TaskStatus.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;

public abstract class AbstractTaskListPresenterTest {

    private static final Long TASK_ID = 1L;
    private static final String TASK_DEPLOYMENT_ID = "deploymentId";

    @Mock
    protected User identity;

    @Mock
    TranslationService translationServiceMock;

    @Mock
    protected TaskService taskService;

    protected CallerMock<TaskService> callerMockRemoteTaskService;

    @Mock
    protected DataSetQueryHelper dataSetQueryHelper;

    @Mock
    protected DataSetQueryHelper dataSetQueryHelperDomainSpecific;

    @Mock
    protected TaskListViewImpl viewMock;

    @Mock
    protected ListTable<TaskSummary> extendedPagedTable;

    @Mock
    protected DataSet dataSetMock;

    @Mock
    protected DataSet dataSetTaskVarMock;

    @Mock
    protected PlaceManager placeManager;

    @Mock
    protected UberfireBreadcrumbs breadcrumbs;

    @Mock
    protected PerspectiveManager perspectiveManager;

    @Mock
    protected PerspectiveActivity perspectiveActivity;

    @Spy
    protected FilterSettings filterSettings;

    @Spy
    protected DataSetLookup dataSetLookup;

    @Spy
    protected Event<TaskSelectionEvent> taskSelected = new EventSourceMock<TaskSelectionEvent>();

    protected static void testTaskStatusCondition(Predicate<TaskSummary> predicate,
                                                  String... validStatutes) {
        List<String> allStatus = getStatusByType(TaskType.ALL);
        final List<String> validStatuses = Arrays.asList(validStatutes);
        allStatus.removeAll(validStatuses);

        allStatus.forEach(s -> assertFalse(predicate.test(TaskSummary.builder().status(s).build())));
        validStatuses.forEach(s -> assertTrue(predicate.test(TaskSummary.builder().status(s).build())));
    }

    @Before
    public void setupMocks() {
        callerMockRemoteTaskService = new CallerMock<TaskService>(taskService);
        getPresenter().setTaskService(callerMockRemoteTaskService);

        doNothing().when(taskSelected).fire(any(TaskSelectionEvent.class));

        //Mock that actually calls the callbacks
        dataSetLookup.setDataSetUUID(HUMAN_TASKS_DATASET);

        when(filterSettings.getDataSetLookup()).thenReturn(dataSetLookup);

        when(viewMock.getListGrid()).thenReturn(extendedPagedTable);
        when(extendedPagedTable.getPageSize()).thenReturn(10);
        when(dataSetQueryHelper.getCurrentTableSettings()).thenReturn(filterSettings);
        when(filterSettings.getKey()).thenReturn("key");
        when(perspectiveManager.getCurrentPerspective()).thenReturn(perspectiveActivity);

        //Mock that actually calls the callbacks
        doAnswer(new Answer() {

            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ((DataSetReadyCallback) invocation.getArguments()[1]).callback(dataSetMock);
                return null;
            }
        }).when(dataSetQueryHelper).lookupDataSet(anyInt(),
                                                  any(DataSetReadyCallback.class));

        //Mock that actually calls the callbacks
        doAnswer(new Answer() {

            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ((DataSetReadyCallback) invocation.getArguments()[1]).callback(dataSetTaskVarMock);
                return null;
            }
        }).when(dataSetQueryHelperDomainSpecific).lookupDataSet(anyInt(),
                                                                any(DataSetReadyCallback.class));

        when(identity.getIdentifier()).thenReturn("userId");
    }

    protected abstract AbstractTaskListPresenter getPresenter();

    protected abstract AbstractTaskListFilterSettingsManager getFilterSettingsManager();

    @Test
    public void getDataTest() {
        getPresenter().getData(new Range(0,
                                         5));

        verify(dataSetQueryHelper).lookupDataSet(anyInt(),
                                                 any(DataSetReadyCallback.class));
        verify(dataSetQueryHelperDomainSpecific,
               never()).lookupDataSet(anyInt(),
                                      any(DataSetReadyCallback.class));
    }

    @Test
    public void releaseTaskTest() {
        final TaskSummary task = TaskSummary.builder().id(TASK_ID).deploymentId(TASK_DEPLOYMENT_ID).build();

        getPresenter().releaseTask(task);

        verify(taskService).releaseTask("",
                                        TASK_DEPLOYMENT_ID,
                                        TASK_ID);
    }

    @Test
    public void claimTaskTest() {
        final TaskSummary task = TaskSummary.builder().id(TASK_ID).deploymentId(TASK_DEPLOYMENT_ID).build();

        getPresenter().claimTask(task);

        verify(taskService).claimTask("",
                                      TASK_DEPLOYMENT_ID,
                                      TASK_ID);
    }

    @Test
    public void resumeTaskTest() {
        final TaskSummary task = TaskSummary.builder().id(TASK_ID).deploymentId(TASK_DEPLOYMENT_ID).build();

        getPresenter().resumeTask(task);

        verify(taskService).resumeTask("",
                                       TASK_DEPLOYMENT_ID,
                                       TASK_ID);
    }

    @Test
    public void suspendTaskTest() {
        final TaskSummary task = TaskSummary.builder().id(TASK_ID).deploymentId(TASK_DEPLOYMENT_ID).build();

        getPresenter().suspendTask(task);

        verify(taskService).suspendTask("",
                                        TASK_DEPLOYMENT_ID,
                                        TASK_ID);
    }

    @Test
    public void isFilteredByTaskNameTest() {
        final String taskName = "taskName";
        final DataSetFilter filter = new DataSetFilter();
        filter.addFilterColumn(equalsTo(COLUMN_NAME,
                                        taskName));

        final String filterTaskName = getPresenter().isFilteredByTaskName(Collections.<DataSetOp>singletonList(filter));
        assertEquals(taskName,
                     filterTaskName);
    }

    @Test
    public void isFilteredByTaskNameInvalidTest() {
        final String taskName = "taskName";
        final DataSetFilter filter = new DataSetFilter();
        filter.addFilterColumn(likeTo(COLUMN_DESCRIPTION,
                                      taskName));

        final String filterTaskName = getPresenter().isFilteredByTaskName(Collections.<DataSetOp>singletonList(filter));
        assertNull(filterTaskName);
    }

    @Test
    public void getDomainSpecificDataForTasksTest() {
        final DataSetFilter filter = new DataSetFilter();
        filter.addFilterColumn(equalsTo(COLUMN_NAME,
                                        "taskName"));
        filterSettings.getDataSetLookup().addOperation(filter);

        when(dataSetMock.getRowCount()).thenReturn(1);//1 task
        //Task summary creation
        when(dataSetMock.getValueAt(0,
                                    COLUMN_TASK_ID)).thenReturn(Long.valueOf(1));

        when(dataSetTaskVarMock.getRowCount()).thenReturn(2); //two domain variables associated
        when(dataSetTaskVarMock.getValueAt(0,
                                           COLUMN_TASK_ID
        )).thenReturn(Long.valueOf(1));

        String taskVariable1 = "var1";
        when(dataSetTaskVarMock.getValueAt(0,
                                           COLUMN_TASK_VARIABLE_NAME)).thenReturn(taskVariable1);
        when(dataSetTaskVarMock.getValueAt(0,
                                           COLUMN_TASK_VARIABLE_VALUE)).thenReturn("value1");

        when(dataSetTaskVarMock.getValueAt(1,
                                           COLUMN_TASK_ID)).thenReturn(Long.valueOf(1));
        String taskVariable2 = "var2";
        when(dataSetTaskVarMock.getValueAt(1,
                                           COLUMN_TASK_VARIABLE_NAME)).thenReturn(taskVariable2);
        when(dataSetTaskVarMock.getValueAt(1,
                                           COLUMN_TASK_VARIABLE_VALUE)).thenReturn("value2");

        Set<String> expectedColumns = new HashSet<String>();
        expectedColumns.add(taskVariable1);
        expectedColumns.add(taskVariable2);

        getPresenter().getData(new Range(0,
                                         5));

        ArgumentCaptor<Set> argument = ArgumentCaptor.forClass(Set.class);
        verify(viewMock).addDomainSpecifColumns(any(ListTable.class),
                                                argument.capture());

        assertEquals(expectedColumns,
                     argument.getValue());

        verify(dataSetQueryHelper).lookupDataSet(anyInt(),
                                                 any(DataSetReadyCallback.class));
        verify(dataSetQueryHelperDomainSpecific).lookupDataSet(anyInt(),
                                                               any(DataSetReadyCallback.class));

        when(dataSetTaskVarMock.getRowCount()).thenReturn(1); //one domain variables associated
        when(dataSetTaskVarMock.getValueAt(0,
                                           COLUMN_TASK_ID)).thenReturn(Long.valueOf(1));
        taskVariable1 = "varTest1";
        when(dataSetTaskVarMock.getValueAt(0,
                                           COLUMN_TASK_VARIABLE_NAME)).thenReturn(taskVariable1);
        when(dataSetTaskVarMock.getValueAt(0,
                                           COLUMN_TASK_VARIABLE_VALUE)).thenReturn("value1");

        expectedColumns = Collections.singleton(taskVariable1);

        getPresenter().getData(new Range(0,
                                         5));

        argument = ArgumentCaptor.forClass(Set.class);
        verify(viewMock,
               times(2)).addDomainSpecifColumns(any(ListTable.class),
                                                argument.capture());

        assertEquals(expectedColumns,
                     argument.getValue());
        verify(dataSetQueryHelper,
               times(2)).lookupDataSet(anyInt(),
                                       any(DataSetReadyCallback.class));
        verify(dataSetQueryHelperDomainSpecific,
               times(2)).lookupDataSet(anyInt(),
                                       any(DataSetReadyCallback.class));
    }

    @Test
    public void testTaskSummaryAdmin() {
        final List<String> dataSets = Arrays.asList(
                HUMAN_TASKS_WITH_ADMIN_DATASET,
                HUMAN_TASKS_WITH_USER_DATASET,
                HUMAN_TASKS_DATASET,
                HUMAN_TASKS_WITH_VARIABLES_DATASET);

        for (final String dataSet : dataSets) {
            when(dataSetMock.getUUID()).thenReturn(dataSet);

            final TaskSummary summary = new TaskSummaryDataSetMapper().apply(dataSetMock,
                                                                             0);
            assertNotNull(summary);
            assertEquals(HUMAN_TASKS_WITH_ADMIN_DATASET.equals(dataSet),
                         summary.isForAdmin());
        }
    }

    @Test
    public void testDefaultActiveSearchFilters() {
        when(translationServiceMock.format(TASK_STATUS_READY.getIdentifier())).thenReturn("Ready");
        when(translationServiceMock.format(TASK_STATUS_IN_PROGRESS.getIdentifier())).thenReturn("InProgress");
        when(translationServiceMock.format(TASK_STATUS_RESERVED.getIdentifier())).thenReturn("Reserved");

        getPresenter().setupDefaultActiveSearchFilters();

        ArgumentCaptor<ActiveFilterItem> captor = ArgumentCaptor.forClass(ActiveFilterItem.class);
        verify(viewMock).addActiveFilter(captor.capture());

        final ActiveFilterItem filterItem = captor.getValue();
        assertNotNull(filterItem);
        assertEquals(Constants.INSTANCE.Status(),
                     filterItem.getKey());
        assertEquals(Arrays.asList(TASK_STATUS_READY.getIdentifier(),
                                   TASK_STATUS_IN_PROGRESS.getIdentifier(),
                                   TASK_STATUS_RESERVED.getIdentifier()),
                     filterItem.getValue());
        assertEquals("Status: Ready, InProgress, Reserved",
                     filterItem.getLabelValue());
    }

    @Test
    public void testCompleteActionCondition() {
        assertTrue(getPresenter().getCompleteActionCondition().test(TaskSummary.builder().actualOwner(identity.getIdentifier()).status(TASK_STATUS_IN_PROGRESS.getIdentifier()).build()));
        assertFalse(getPresenter().getCompleteActionCondition().test(TaskSummary.builder().actualOwner(identity.getIdentifier()).status(TASK_STATUS_READY.getIdentifier()).build()));
    }

    @Test
    public void testClaimActionCondition() {
        testTaskStatusCondition(getPresenter().getClaimActionCondition(),
                                TASK_STATUS_READY.getIdentifier());
    }

    @Test
    public void testReleaseActionCondition() {
        assertTrue(getPresenter().getReleaseActionCondition().test(TaskSummary.builder().actualOwner(identity.getIdentifier()).status(TASK_STATUS_RESERVED.getIdentifier()).build()));
        assertTrue(getPresenter().getReleaseActionCondition().test(TaskSummary.builder().actualOwner(identity.getIdentifier()).status(TASK_STATUS_IN_PROGRESS.getIdentifier()).build()));
        assertFalse(getPresenter().getReleaseActionCondition().test(TaskSummary.builder().actualOwner(identity.getIdentifier()).status(TASK_STATUS_COMPLETED.getIdentifier()).build()));
        assertFalse(getPresenter().getReleaseActionCondition().test(TaskSummary.builder().actualOwner(identity.getIdentifier()).status(TASK_STATUS_CREATED.getIdentifier()).build()));
    }

    @Test
    public void testProcessInstanceCondition() {
        assertTrue(getPresenter().getProcessInstanceCondition().test(TaskSummary.builder().processInstanceId(1l).build()));
        assertFalse(getPresenter().getProcessInstanceCondition().test(TaskSummary.builder().build()));
    }

    @Test
    public void testCreateDataSetTaskCallback() {
        final AbstractTaskListPresenter presenter = spy(getPresenter());
        final ClientRuntimeError error = new ClientRuntimeError("");
        final FilterSettings filterSettings = mock(FilterSettings.class);
        final DataSetReadyCallback callback = presenter.getDataSetReadyCallback(0,
                                                                                filterSettings);

        doNothing().when(presenter).showErrorPopup(any());

        assertFalse(callback.onError(error));

        verify(viewMock).hideBusyIndicator();
        verify(presenter).showErrorPopup(Constants.INSTANCE.TaskListCouldNotBeLoaded());
    }

    @Test
    public void testExitedTaskSelection() {
        TaskSummary taskSummary = TaskSummary.builder()
                .id(TASK_ID)
                .deploymentId(TASK_DEPLOYMENT_ID)
                .status(TASK_STATUS_EXITED.getIdentifier())
                .build();

        getPresenter().selectTask(taskSummary);

        verify(placeManager).goTo(PerspectiveIds.TASK_DETAILS_SCREEN);
        final ArgumentCaptor<TaskSelectionEvent> captor = ArgumentCaptor.forClass(TaskSelectionEvent.class);
        verify(taskSelected).fire(captor.capture());
        assertTrue(captor.getValue().isForLog());
        assertEquals(taskSummary, getPresenter().getSelectedTask());
    }

    @Test
    public void testReadyTaskSelection() {
        TaskSummary taskSummary = TaskSummary.builder()
                .id(TASK_ID)
                .deploymentId(TASK_DEPLOYMENT_ID)
                .status(TASK_STATUS_READY.getIdentifier())
                .build();

        getPresenter().selectTask(taskSummary);

        verify(placeManager).goTo(PerspectiveIds.TASK_DETAILS_SCREEN);
        final ArgumentCaptor<TaskSelectionEvent> captor = ArgumentCaptor.forClass(TaskSelectionEvent.class);
        verify(taskSelected).fire(captor.capture());
        assertFalse(captor.getValue().isForLog());
        assertEquals(taskSummary, getPresenter().getSelectedTask());
    }

    @Test
    public void testOnBasicFilterAddEvent() {
        final ActiveFilterItem<Object> filter = new ActiveFilterItem<>("key1",
                                                                       null,
                                                                       null,
                                                                       null,
                                                                       null);
        final ColumnFilter columnFilter = mock(ColumnFilter.class);
        getPresenter().onBasicFilterAddEvent(new BasicFilterAddEvent(filter,
                                                                columnFilter));

        verify(viewMock).addActiveFilter(filter);
        verify(filterSettings).addColumnFilter(columnFilter);
    }

    @Test
    public void testOnBasicFilterRemoveEvent() {
        final ActiveFilterItem<Object> filter = new ActiveFilterItem<>("key1",
                                                                       null,
                                                                       null,
                                                                       null,
                                                                       null);
        final ColumnFilter columnFilter = mock(ColumnFilter.class);
        getPresenter().onBasicFilterRemoveEvent(new BasicFilterRemoveEvent(filter,
                                                                      columnFilter));

        verify(viewMock).removeActiveFilter(filter);
        verify(filterSettings).removeColumnFilter(columnFilter);
    }

    @Test
    public void testTaskIsSameFromEvent() {
        final String serverTemplateId = "serverTemplateId";

        assertFalse(getPresenter().isSameTaskFromEvent().test(new TaskCompletedEvent(serverTemplateId,
                                                                                    TASK_DEPLOYMENT_ID,
                                                                                    TASK_ID)));

        TaskSummary taskSummary = TaskSummary.builder()
                .id(TASK_ID)
                .deploymentId(TASK_DEPLOYMENT_ID)
                .status(TASK_STATUS_READY.getIdentifier())
                .build();


        getPresenter().setSelectedServerTemplate(serverTemplateId);
        getPresenter().selectTask(taskSummary);

        assertTrue(getPresenter().isSameTaskFromEvent().test(new TaskCompletedEvent(serverTemplateId,
                                                                                    TASK_DEPLOYMENT_ID,
                                                                                    TASK_ID)));

        assertFalse(getPresenter().isSameTaskFromEvent().test(new TaskCompletedEvent(serverTemplateId,
                                                                                     TASK_DEPLOYMENT_ID,
                                                                                     2l)));
        assertFalse(getPresenter().isSameTaskFromEvent().test(new TaskCompletedEvent("anotherServerTemplateId",
                                                                                     TASK_DEPLOYMENT_ID,
                                                                                     TASK_ID)));
        assertFalse(getPresenter().isSameTaskFromEvent().test(new TaskCompletedEvent(serverTemplateId,
                                                                                     "anotherContainerId",
                                                                                     TASK_ID)));
    }

    @Test
    public void testOnTaskCompletedEvent() {
        TaskSummary taskSummary = TaskSummary.builder()
                .id(TASK_ID)
                .deploymentId(TASK_DEPLOYMENT_ID)
                .status(TASK_STATUS_READY.getIdentifier())
                .build();

        final String serverTemplateId = "serverTemplateId";
        getPresenter().setSelectedServerTemplate(serverTemplateId);
        getPresenter().selectTask(taskSummary);

        getPresenter().onTaskCompletedEvent(new TaskCompletedEvent(serverTemplateId,
                                                                   TASK_DEPLOYMENT_ID,
                                                                   TASK_ID));

        getPresenter().onTaskCompletedEvent(new TaskCompletedEvent(serverTemplateId,
                                                                   TASK_DEPLOYMENT_ID,
                                                                   2l));

        //Refreshed 2 times only, one for server template and only one task event
        verify(extendedPagedTable,
               times(2)).setVisibleRangeAndClearData(any(),
                                                     eq(true));
    }

    protected static BeforeClosePlaceEvent newCloseEventMock(String placeId) {
        final BeforeClosePlaceEvent event = mock(BeforeClosePlaceEvent.class);
        when(event.getPlace()).thenReturn(new DefaultPlaceRequest(placeId));
        return event;
    }

    @Test
    public void testOnTaskDetailsClosed() {
        TaskSummary taskSummary = TaskSummary.builder()
                .id(TASK_ID)
                .deploymentId(TASK_DEPLOYMENT_ID)
                .status(TASK_STATUS_EXITED.getIdentifier())
                .build();

        getPresenter().selectTask(taskSummary);

        assertEquals(taskSummary,
                     getPresenter().getSelectedTask());

        getPresenter().onTaskDetailsClosed(newCloseEventMock(PerspectiveIds.TASK_ADMIN_LIST_SCREEN));

        assertEquals(taskSummary,
                     getPresenter().getSelectedTask());

        getPresenter().onTaskDetailsClosed(newCloseEventMock(PerspectiveIds.TASK_DETAILS_SCREEN));

        assertNull(getPresenter().getSelectedTask());
    }
}
