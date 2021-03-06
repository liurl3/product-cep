/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.integration.test.inputflow;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.databridge.commons.Event;
import org.wso2.carbon.integration.test.client.SOAPEventPublisherClient;
import org.wso2.carbon.integration.test.client.Wso2EventServer;
import org.wso2.cep.integration.common.utils.CEPIntegrationTest;
import org.wso2.cep.integration.common.utils.CEPIntegrationTestConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Sending different formatted events to the SOAP Receiver according to the receivers mapping type
 */
public class SOAPTestCase extends CEPIntegrationTest {

    private static final Log log = LogFactory.getLog(SOAPTestCase.class);

    @BeforeClass(alwaysRun = true)
    public void init()
            throws Exception {

        super.init(TestUserMode.SUPER_TENANT_ADMIN);
        String loggedInSessionCookie = getSessionCookie();

        eventReceiverAdminServiceClient = configurationUtil.getEventReceiverAdminServiceClient(backendURL,
                loggedInSessionCookie);
        eventStreamManagerAdminServiceClient = configurationUtil.getEventStreamManagerAdminServiceClient(backendURL,
                loggedInSessionCookie);
        eventPublisherAdminServiceClient = configurationUtil.getEventPublisherAdminServiceClient(backendURL,
                loggedInSessionCookie);
    }


    @Test(groups = {"wso2.cep"}, description = "Testing soap receiver with XML formatted event")
    public void SOAPXMLTestScenario() throws Exception {
        String samplePath = "inputflows" + File.separator + "sample0014";
        int startESCount = eventStreamManagerAdminServiceClient.getEventStreamCount();
        int startERCount = eventReceiverAdminServiceClient.getActiveEventReceiverCount();
        int startEPCount = eventPublisherAdminServiceClient.getActiveEventPublisherCount();

        //Add StreamDefinition
        String streamDefinitionAsString = getJSONArtifactConfiguration(samplePath,
                "org.wso2.event.sensor.stream_1.0.0.json");
        eventStreamManagerAdminServiceClient.addEventStreamAsString(streamDefinitionAsString);
        Assert.assertEquals(eventStreamManagerAdminServiceClient.getEventStreamCount(), startESCount + 1);

        //Add SOAP JSON EventReceiver without mapping
        String eventReceiverConfig = getXMLArtifactConfiguration(samplePath, "soapReceiver.xml");
        eventReceiverAdminServiceClient.addEventReceiverConfiguration(eventReceiverConfig);
        Assert.assertEquals(eventReceiverAdminServiceClient.getActiveEventReceiverCount(), startERCount + 1);

        //Add Wso2event EventPublisher
        String eventPublisherConfig2 = getXMLArtifactConfiguration(samplePath, "wso2EventPublisher.xml");
        eventPublisherAdminServiceClient.addEventPublisherConfiguration(eventPublisherConfig2);
        Assert.assertEquals(eventPublisherAdminServiceClient.getActiveEventPublisherCount(), startEPCount + 1);

        // The data-bridge receiver
        Wso2EventServer agentServer = new Wso2EventServer(samplePath, CEPIntegrationTestConstants.TCP_PORT, true);
        Thread agentServerThread = new Thread(agentServer);
        agentServerThread.start();
        // Let the server start
        Thread.sleep(10000);

        SOAPEventPublisherClient.publish("http://localhost:" + CEPIntegrationTestConstants.HTTP_PORT +
                                         "/services/soapReceiver/receive", samplePath, "soapReceiver.txt");

        //wait while all stats are published
        Thread.sleep(30000);

        eventStreamManagerAdminServiceClient.removeEventStream("org.wso2.event.sensor.stream", "1.0.0");
        eventReceiverAdminServiceClient.removeInactiveEventReceiverConfiguration("soapReceiver.xml");
        eventPublisherAdminServiceClient.removeInactiveEventPublisherConfiguration("wso2EventPublisher.xml");

        Thread.sleep(2000);

        List<Event> eventList = new ArrayList<>();
        Event event = new Event();
        event.setStreamId("org.wso2.event.sensor.stream:1.0.0");
        event.setMetaData(new Object[]{4354643, true, 100, "data1"});
        event.setCorrelationData(new Object[]{90.34344, 5.443435});
        event.setPayloadData(new Object[]{8.9f, 20.44345});
        eventList.add(event);
        Event event2 = new Event();
        event2.setStreamId("org.wso2.event.sensor.stream:1.0.0");
        event2.setMetaData(new Object[]{4354653, false, 101, "data1"});
        event2.setCorrelationData(new Object[]{90.34344, 5.443435});
        event2.setPayloadData(new Object[]{8.9f, 20.44345});
        eventList.add(event2);
        Event event3 = new Event();
        event3.setStreamId("org.wso2.event.sensor.stream:1.0.0");
        event3.setMetaData(new Object[]{4354343, true, 102, "data1"});
        event3.setCorrelationData(new Object[]{90.34344, 5.443435});
        event3.setPayloadData(new Object[]{8.9f, 20.44345});
        eventList.add(event3);

        try {
            Assert.assertEquals(agentServer.getMsgCount(), eventList.size(), "Incorrect number of messages consumed!");

            int counter = 0;
            for (Event currentEvent : agentServer.getPreservedEventList()) {
                currentEvent.setTimeStamp(0);
                Assert.assertEquals(currentEvent.toString(), eventList.get(counter).toString(), "Mapping is incorrect!");
                counter++;
            }


        } catch (Throwable e) {
            log.error("Exception thrown: " + e.getMessage(), e);
            Assert.fail("Exception: " + e.getMessage());
        } finally {
            agentServer.stop();

        }
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        super.cleanup();
    }
}
