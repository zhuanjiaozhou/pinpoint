/*
 * Copyright 2017 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.web.applicationmap;

import com.navercorp.pinpoint.common.server.util.AgentLifeCycleState;
import com.navercorp.pinpoint.common.trace.ServiceType;
import com.navercorp.pinpoint.web.applicationmap.appender.histogram.DefaultNodeHistogramFactory;
import com.navercorp.pinpoint.web.applicationmap.appender.histogram.NodeHistogramFactory;
import com.navercorp.pinpoint.web.applicationmap.appender.histogram.datasource.MapResponseNodeHistogramDataSource;
import com.navercorp.pinpoint.web.applicationmap.appender.histogram.datasource.ResponseHistogramBuilderNodeHistogramDataSource;
import com.navercorp.pinpoint.web.applicationmap.appender.server.DefaultServerInstanceListFactory;
import com.navercorp.pinpoint.web.applicationmap.appender.server.ServerInstanceListFactory;
import com.navercorp.pinpoint.web.applicationmap.appender.server.datasource.AgentInfoServerInstanceListDataSource;
import com.navercorp.pinpoint.web.applicationmap.rawdata.LinkDataDuplexMap;
import com.navercorp.pinpoint.web.dao.MapResponseDao;
import com.navercorp.pinpoint.web.service.AgentInfoService;
import com.navercorp.pinpoint.web.vo.AgentInfo;
import com.navercorp.pinpoint.web.vo.AgentStatus;
import com.navercorp.pinpoint.web.vo.Application;
import com.navercorp.pinpoint.web.vo.Range;
import com.navercorp.pinpoint.web.vo.ResponseHistogramBuilder;
import com.navercorp.pinpoint.web.vo.ResponseTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author HyunGil Jeong
 */
public class ApplicationMapBuilderCompatibilityTest {

    private MapResponseNodeHistogramDataSource mapResponseNodeHistogramDataSource;

    private ResponseHistogramBuilderNodeHistogramDataSource responseHistogramBuilderNodeHistogramDataSource;

    private AgentInfoServerInstanceListDataSource agentInfoServerInstanceListDataSource;

    @Before
    public void setUp() {
        MapResponseDao mapResponseDao = mock(MapResponseDao.class);
        mapResponseNodeHistogramDataSource = new MapResponseNodeHistogramDataSource(mapResponseDao);

        ResponseHistogramBuilder responseHistogramBuilder = mock(ResponseHistogramBuilder.class);
        responseHistogramBuilderNodeHistogramDataSource = new ResponseHistogramBuilderNodeHistogramDataSource(responseHistogramBuilder);

        AgentInfoService agentInfoService = mock(AgentInfoService.class);
        agentInfoServerInstanceListDataSource = new AgentInfoServerInstanceListDataSource(agentInfoService);

        Answer<List<ResponseTime>> responseTimeAnswer = new Answer<List<ResponseTime>>() {
            long timestamp = System.currentTimeMillis();
            @Override
            public List<ResponseTime> answer(InvocationOnMock invocation) throws Throwable {
                Application application = invocation.getArgument(0);
                String applicationName = application.getName();
                ServiceType applicationServiceType = application.getServiceType();
                int depth = ApplicationMapBuilderTestHelper.getDepthFromApplicationName(applicationName);
                ResponseTime responseTime = new ResponseTime(application.getName(), application.getServiceType(), timestamp);
                responseTime.addResponseTime(ApplicationMapBuilderTestHelper.createAgentIdFromDepth(depth), applicationServiceType.getHistogramSchema().getNormalSlot().getSlotTime(), 1);
                return Collections.singletonList(responseTime);
            }
        };
        when(mapResponseDao.selectResponseTime(any(Application.class), any(Range.class))).thenAnswer(responseTimeAnswer);
        when(responseHistogramBuilder.getResponseTimeList(any(Application.class))).thenAnswer(responseTimeAnswer);

        when(agentInfoService.getAgentsByApplicationName(anyString(), anyLong())).thenAnswer(new Answer<Set<AgentInfo>>() {
            @Override
            public Set<AgentInfo> answer(InvocationOnMock invocation) throws Throwable {
                String applicationName = invocation.getArgument(0);
                AgentInfo agentInfo = ApplicationMapBuilderTestHelper.createAgentInfoFromApplicationName(applicationName);
                AgentStatus agentStatus = new AgentStatus(agentInfo.getAgentId());
                agentStatus.setState(AgentLifeCycleState.RUNNING);
                agentInfo.setStatus(agentStatus);
                Set<AgentInfo> agentInfos = new HashSet<>();
                agentInfos.add(agentInfo);
                return agentInfos;
            }
        });
        when(agentInfoService.getAgentsByApplicationNameWithoutStatus(anyString(), anyLong())).thenAnswer(new Answer<Set<AgentInfo>>() {
            @Override
            public Set<AgentInfo> answer(InvocationOnMock invocation) throws Throwable {
                String applicationName = invocation.getArgument(0);
                AgentInfo agentInfo = ApplicationMapBuilderTestHelper.createAgentInfoFromApplicationName(applicationName);
                Set<AgentInfo> agentInfos = new HashSet<>();
                agentInfos.add(agentInfo);
                return agentInfos;
            }
        });
        when(agentInfoService.getAgentStatus(anyString(), anyLong())).thenAnswer(new Answer<AgentStatus>()  {
            @Override
            public AgentStatus answer(InvocationOnMock invocation) throws Throwable {
                String agentId = invocation.getArgument(0);
                AgentStatus agentStatus = new AgentStatus(agentId);
                agentStatus.setEventTimestamp(System.currentTimeMillis());
                agentStatus.setState(AgentLifeCycleState.RUNNING);
                return agentStatus;
            }
        });
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Collection<AgentInfo> agentInfos = invocation.getArgument(0);
                for (AgentInfo agentInfo : agentInfos) {
                    AgentStatus agentStatus = new AgentStatus(agentInfo.getAgentId());
                    agentStatus.setEventTimestamp(System.currentTimeMillis());
                    agentStatus.setState(AgentLifeCycleState.RUNNING);
                    agentInfo.setStatus(agentStatus);
                }
                return null;
            }
        }).when(agentInfoService).populateAgentStatuses(anyCollection(), anyLong());
    }

    @Test
    public void testNoCallData() {
        Range range = new Range(0, 1000);
        Application application = ApplicationMapBuilderTestHelper.createApplicationFromDepth(0);

        ServerInstanceListFactory serverInstanceListFactory = new DefaultServerInstanceListFactory(agentInfoServerInstanceListDataSource);

        ApplicationMapBuilder applicationMapBuilderV1 = ApplicationMapBuilderTestHelper.createApplicationMapBuilderV1(range);
        ApplicationMapBuilder applicationMapBuilderV2 = ApplicationMapBuilderTestHelper.createApplicationMapBuilderV2(range);
        ApplicationMapBuilder applicationMapBuilderV2_parallelAppenders = ApplicationMapBuilderTestHelper.createApplicationMapBuilderV2_parallelAppenders(range);
        ApplicationMap applicationMapV1 = applicationMapBuilderV1
                .includeServerInfo(serverInstanceListFactory)
                .build(application);
        ApplicationMap applicationMapV2 = applicationMapBuilderV2
                .includeServerInfo(serverInstanceListFactory)
                .build(application);
        ApplicationMap applicationMapV2_parallelAppenders = applicationMapBuilderV2_parallelAppenders
                .includeServerInfo(serverInstanceListFactory)
                .build(application);

        Assert.assertEquals(1, applicationMapV1.getNodes().size());
        Assert.assertEquals(1, applicationMapV2.getNodes().size());
        Assert.assertEquals(1, applicationMapV2_parallelAppenders.getNodes().size());
        Assert.assertEquals(0, applicationMapV1.getLinks().size());
        Assert.assertEquals(0, applicationMapV2.getLinks().size());
        Assert.assertEquals(0, applicationMapV2_parallelAppenders.getLinks().size());

        ApplicationMapVerifier verifier = new ApplicationMapVerifier(applicationMapV1);
        verifier.verify(applicationMapV2);
        verifier.verify(applicationMapV2_parallelAppenders);
    }

    @Test
    public void testEmptyCallData() {
        Range range = new Range(0, 1000);
        LinkDataDuplexMap linkDataDuplexMap = new LinkDataDuplexMap();

        NodeHistogramFactory nodeHistogramFactory = new DefaultNodeHistogramFactory(mapResponseNodeHistogramDataSource);
        ServerInstanceListFactory serverInstanceListFactory = new DefaultServerInstanceListFactory(agentInfoServerInstanceListDataSource);

        ApplicationMapBuilder applicationMapBuilderV1 = ApplicationMapBuilderTestHelper.createApplicationMapBuilderV1(range);
        ApplicationMapBuilder applicationMapBuilderV2 = ApplicationMapBuilderTestHelper.createApplicationMapBuilderV2(range);
        ApplicationMapBuilder applicationMapBuilderV2_parallelAppenders = ApplicationMapBuilderTestHelper.createApplicationMapBuilderV2_parallelAppenders(range);
        ApplicationMap applicationMapV1 = applicationMapBuilderV1
                .includeNodeHistogram(nodeHistogramFactory)
                .includeServerInfo(serverInstanceListFactory)
                .build(linkDataDuplexMap);
        ApplicationMap applicationMapV2 = applicationMapBuilderV2
                .includeNodeHistogram(nodeHistogramFactory)
                .includeServerInfo(serverInstanceListFactory)
                .build(linkDataDuplexMap);
        ApplicationMap applicationMapV2_parallelAppenders = applicationMapBuilderV2_parallelAppenders
                .includeNodeHistogram(nodeHistogramFactory)
                .includeServerInfo(serverInstanceListFactory)
                .build(linkDataDuplexMap);

        Assert.assertTrue(applicationMapV1.getNodes().isEmpty());
        Assert.assertTrue(applicationMapV2.getNodes().isEmpty());
        Assert.assertTrue(applicationMapV2_parallelAppenders.getNodes().isEmpty());
        Assert.assertTrue(applicationMapV1.getLinks().isEmpty());
        Assert.assertTrue(applicationMapV2.getLinks().isEmpty());
        Assert.assertTrue(applicationMapV2_parallelAppenders.getLinks().isEmpty());

        ApplicationMapVerifier verifier = new ApplicationMapVerifier(applicationMapV1);
        verifier.verify(applicationMapV2);
        verifier.verify(applicationMapV2_parallelAppenders);
    }

    /**
     * USER -> WAS(center) -> UNKNOWN
     */
    @Test
    public void testOneDepth() {
        int depth = 1;
        runTest(depth, depth);
    }

    /**
     * USER -> WAS -> WAS(center) -> WAS -> UNKNOWN
     */
    @Test
    public void testTwoDepth() {
        int depth = 2;
        runTest(depth, depth);
    }

    /**
     * USER -> WAS -> WAS -> WAS(center) -> WAS -> WAS -> UNKNOWN
     */
    @Test
    public void testThreeDepth() {
        int depth = 3;
        runTest(depth, depth);
    }

    /**
     * USER -> WAS(center) -> WAS -> WAS -> UNKNOWN
     */
    @Test
    public void test_1_3_depth() {
        int calleeDepth = 1;
        int callerDepth = 3;
        runTest(calleeDepth, callerDepth);
    }

    /**
     * USER -> WAS -> WAS -> WAS(center) -> UNKNOWN
     */
    @Test
    public void test_3_1_depth() {
        int calleeDepth = 3;
        int callerDepth = 1;
        runTest(calleeDepth, callerDepth);
    }

    /**
     * USER -> WAS -> WAS -> WAS(center) -> WAS -> WAS -> WAS -> UNKNOWN
     */
    @Test
    public void test_3_4_depth() {
        int calleeDepth = 3;
        int callerDepth = 4;
        runTest(calleeDepth, callerDepth);
    }

    /**
     * USER -> WAS -> WAS -> WAS -> WAS(center) -> WAS -> WAS -> UNKNOWN
     */
    @Test
    public void test_4_3_depth() {
        int calleeDepth = 4;
        int callerDepth = 3;
        runTest(calleeDepth, callerDepth);
    }

    private void runTest(int callerDepth, int calleeDepth) {
        Range range = new Range(0, 1000);
        int expectedNumNodes = ApplicationMapBuilderTestHelper.getExpectedNumNodes(calleeDepth, callerDepth);
        int expectedNumLinks = ApplicationMapBuilderTestHelper.getExpectedNumLinks(calleeDepth, callerDepth);

        NodeHistogramFactory nodeHistogramFactory_MapResponseDao = new DefaultNodeHistogramFactory(mapResponseNodeHistogramDataSource);
        NodeHistogramFactory nodeHistogramFactory_ResponseHistogramBuilder = new DefaultNodeHistogramFactory(responseHistogramBuilderNodeHistogramDataSource);
        ServerInstanceListFactory serverInstanceListFactory = new DefaultServerInstanceListFactory(agentInfoServerInstanceListDataSource);

        LinkDataDuplexMap linkDataDuplexMap = ApplicationMapBuilderTestHelper.createLinkDataDuplexMap(calleeDepth, callerDepth);
        ApplicationMapBuilder applicationMapBuilderV1 = ApplicationMapBuilderTestHelper.createApplicationMapBuilderV1(range);
        ApplicationMapBuilder applicationMapBuilderV2 = ApplicationMapBuilderTestHelper.createApplicationMapBuilderV2(range);
        ApplicationMapBuilder applicationMapBuilderV2_parallelAppenders = ApplicationMapBuilderTestHelper.createApplicationMapBuilderV2_parallelAppenders(range);

        // test builder using MapResponseDao
        ApplicationMap applicationMapV1_MapResponseDao = applicationMapBuilderV1
                .includeNodeHistogram(nodeHistogramFactory_MapResponseDao)
                .includeServerInfo(serverInstanceListFactory)
                .build(linkDataDuplexMap);
        ApplicationMap applicationMapV2_MapResponseDao = applicationMapBuilderV2
                .includeNodeHistogram(nodeHistogramFactory_MapResponseDao)
                .includeServerInfo(serverInstanceListFactory)
                .build(linkDataDuplexMap);
        ApplicationMap applicationMapV2_MapResponseDao_parallelAppenders = applicationMapBuilderV2_parallelAppenders
                .includeNodeHistogram(nodeHistogramFactory_MapResponseDao)
                .includeServerInfo(serverInstanceListFactory)
                .build(linkDataDuplexMap);
        Assert.assertEquals(expectedNumNodes, applicationMapV1_MapResponseDao.getNodes().size());
        Assert.assertEquals(expectedNumNodes, applicationMapV2_MapResponseDao.getNodes().size());
        Assert.assertEquals(expectedNumNodes, applicationMapV2_MapResponseDao_parallelAppenders.getNodes().size());
        Assert.assertEquals(expectedNumLinks, applicationMapV1_MapResponseDao.getLinks().size());
        Assert.assertEquals(expectedNumLinks, applicationMapV2_MapResponseDao.getLinks().size());
        Assert.assertEquals(expectedNumLinks, applicationMapV2_MapResponseDao_parallelAppenders.getLinks().size());
        ApplicationMapVerifier verifier_MapResponseDao = new ApplicationMapVerifier(applicationMapV1_MapResponseDao);
        verifier_MapResponseDao.verify(applicationMapV2_MapResponseDao);
        verifier_MapResponseDao.verify(applicationMapV2_MapResponseDao_parallelAppenders);

        // test builder using ResponseHistogramBuilder
        ApplicationMap applicationMapV1_ResponseHistogramBuilder = applicationMapBuilderV1
                .includeNodeHistogram(nodeHistogramFactory_ResponseHistogramBuilder)
                .includeServerInfo(serverInstanceListFactory)
                .build(linkDataDuplexMap);
        ApplicationMap applicationMapV2_ResponseHistogramBuilder = applicationMapBuilderV2
                .includeNodeHistogram(nodeHistogramFactory_ResponseHistogramBuilder)
                .includeServerInfo(serverInstanceListFactory)
                .build(linkDataDuplexMap);
        ApplicationMap applicationMapV2_ResponseHistogramBuilder_parallelAppenders = applicationMapBuilderV2_parallelAppenders
                .includeNodeHistogram(nodeHistogramFactory_ResponseHistogramBuilder)
                .includeServerInfo(serverInstanceListFactory)
                .build(linkDataDuplexMap);
        Assert.assertEquals(expectedNumNodes, applicationMapV1_ResponseHistogramBuilder.getNodes().size());
        Assert.assertEquals(expectedNumNodes, applicationMapV2_ResponseHistogramBuilder.getNodes().size());
        Assert.assertEquals(expectedNumNodes, applicationMapV2_ResponseHistogramBuilder_parallelAppenders.getNodes().size());
        Assert.assertEquals(expectedNumLinks, applicationMapV1_ResponseHistogramBuilder.getLinks().size());
        Assert.assertEquals(expectedNumLinks, applicationMapV2_ResponseHistogramBuilder.getLinks().size());
        Assert.assertEquals(expectedNumLinks, applicationMapV2_ResponseHistogramBuilder_parallelAppenders.getLinks().size());
        ApplicationMapVerifier verifier_ResponseHistogramBuilder = new ApplicationMapVerifier(applicationMapV1_ResponseHistogramBuilder);
        verifier_ResponseHistogramBuilder.verify(applicationMapV2_ResponseHistogramBuilder);
        verifier_ResponseHistogramBuilder.verify(applicationMapV2_ResponseHistogramBuilder_parallelAppenders);
    }
}
