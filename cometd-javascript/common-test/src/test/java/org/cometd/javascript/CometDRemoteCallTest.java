/*
 * Copyright (c) 2008-2017 the original author or authors.
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
package org.cometd.javascript;

import java.util.HashMap;
import java.util.Map;

import org.cometd.annotation.RemoteCall;
import org.cometd.annotation.ServerAnnotationProcessor;
import org.cometd.annotation.Service;
import org.cometd.server.AbstractServerTransport;
import org.cometd.server.JettyJSONContextServer;
import org.eclipse.jetty.util.ajax.JSON;
import org.junit.Assert;
import org.junit.Test;

public class CometDRemoteCallTest extends AbstractCometDTest {
    @Test
    public void testRemoteCallWithResult() throws Exception {
        ServerAnnotationProcessor processor = new ServerAnnotationProcessor(cometdServlet.getBayeux());
        String response = "response";
        processor.process(new RemoteCallWithResultService(response));

        defineClass(Latch.class);

        evaluateScript("cometd.init({ url: '" + cometdURL + "', logLevel: '" + getLogLevel() + "' });");
        Thread.sleep(1000); // Wait for /meta/connect

        evaluateScript("var latch = new Latch(1);");
        Latch latch = get("latch");

        String request = "request";
        evaluateScript("cometd.remoteCall('" + RemoteCallWithResultService.CHANNEL + "', '" + request + "', " +
                "function(response)" +
                "{" +
                "    window.assert(response.successful === true, 'missing successful field');" +
                "    window.assert(response.data === '" + response + "', 'wrong response');" +
                "    latch.countDown();" +
                "});");

        Assert.assertTrue(latch.await(5000));

        disconnect();
    }

    @Service
    public static class RemoteCallWithResultService {
        private static final String CHANNEL = "/remote_result";
        private final String response;

        public RemoteCallWithResultService(String response) {
            this.response = response;
        }

        @RemoteCall(CHANNEL)
        public void service(RemoteCall.Caller caller, Object data) {
            caller.result(response);
        }
    }

    @Test
    public void testRemoteCallWithFailure() throws Exception {
        ServerAnnotationProcessor processor = new ServerAnnotationProcessor(cometdServlet.getBayeux());
        String failure = "response";
        processor.process(new RemoteCallWithFailureService(failure));

        defineClass(Latch.class);

        evaluateScript("cometd.init({ url: '" + cometdURL + "', logLevel: '" + getLogLevel() + "' });");
        Thread.sleep(1000); // Wait for /meta/connect

        evaluateScript("var latch = new Latch(1);");
        Latch latch = get("latch");

        String request = "request";
        evaluateScript("cometd.remoteCall('" + RemoteCallWithFailureService.CHANNEL + "', '" + request + "', " +
                "function(response)" +
                "{" +
                "    window.assert(response.successful === false, 'missing successful field');" +
                "    window.assert(response.data === '" + failure + "', 'wrong response');" +
                "    latch.countDown();" +
                "});");

        Assert.assertTrue(latch.await(5000));

        disconnect();
    }

    @Service
    public static class RemoteCallWithFailureService {
        private static final String CHANNEL = "/remote_failure";
        private final String failure;

        public RemoteCallWithFailureService(String failure) {
            this.failure = failure;
        }

        @RemoteCall(CHANNEL)
        public void service(RemoteCall.Caller caller, Object data) {
            caller.failure(failure);
        }
    }

    @Test
    public void testRemoteCallTimeout() throws Exception {
        long timeout = 1000;
        ServerAnnotationProcessor processor = new ServerAnnotationProcessor(cometdServlet.getBayeux());
        boolean processed = processor.process(new RemoteCallTimeoutService(timeout));
        Assert.assertTrue(processed);

        defineClass(Latch.class);

        evaluateScript("cometd.init({ url: '" + cometdURL + "', logLevel: '" + getLogLevel() + "' });");
        Thread.sleep(1000); // Wait for /meta/connect

        evaluateScript("var latch = new Latch(1);");
        Latch latch = get("latch");

        evaluateScript("" +
                "var calls = 0;" +
                "cometd.remoteCall('" + RemoteCallTimeoutService.CHANNEL + "', {}, " + timeout + ", " +
                "function(response)" +
                "{" +
                "    ++calls;" +
                "    window.assert(response.successful === false, 'missing successful field');" +
                "    window.assert(response.error !== undefined, 'missing error field');" +
                "    latch.countDown();" +
                "});");

        // Wait enough for the server to actually reply,
        // to verify that the callback is not called twice.
        Thread.sleep(3 * timeout);

        Assert.assertTrue(latch.await(5000));
        Assert.assertEquals(1, ((Number)get("calls")).intValue());

        disconnect();
    }

    @Service
    public static class RemoteCallTimeoutService {
        public static final String CHANNEL = "remote_timeout";

        private final long timeout;

        public RemoteCallTimeoutService(long timeout) {
            this.timeout = timeout;
        }

        @RemoteCall(CHANNEL)
        public void service(final RemoteCall.Caller caller, Object data) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(2 * timeout);
                        caller.result(new HashMap());
                    } catch (InterruptedException x) {
                        caller.failure(x);
                    }
                }
            }).start();
        }
    }

    @Test
    public void testRemoteCallWithCustomDataClass() throws Exception {
        JettyJSONContextServer jsonContext = (JettyJSONContextServer)bayeuxServer.getOption(AbstractServerTransport.JSON_CONTEXT_OPTION);
        jsonContext.getJSON().addConvertor(Custom.class, new CustomConvertor());

        final String request = "request";
        final String response = "response";

        ServerAnnotationProcessor processor = new ServerAnnotationProcessor(cometdServlet.getBayeux());
        boolean processed = processor.process(new RemoteCallWithCustomDataClassService(request, response));
        Assert.assertTrue(processed);

        defineClass(Latch.class);

        evaluateScript("var latch = new Latch(1);");
        Latch latch = get("latch");

        evaluateScript("cometd.init({ url: '" + cometdURL + "', logLevel: '" + getLogLevel() + "' });");
        Thread.sleep(1000); // Wait for /meta/connect

        evaluateScript("" +
                "cometd.remoteCall('" + RemoteCallWithCustomDataClassService.CHANNEL + "', {" +
                "   class: '" + Custom.class.getName() + "'," +
                "   payload: '" + request + "'," +
                "}, " +
                "function(response)" +
                "{" +
                "    window.assert(response.successful === true);" +
                "    window.assert(response.data !== undefined);" +
                "    window.assert(response.data.payload == '" + response + "');" +
                "    latch.countDown();" +
                "});");

        Assert.assertTrue(latch.await(5000));

        disconnect();
    }

    @Service
    public static class RemoteCallWithCustomDataClassService {
        public static final String CHANNEL = "/custom_data_class";

        private String request;
        private String response;

        public RemoteCallWithCustomDataClassService(String request, String response) {
            this.request = request;
            this.response = response;
        }

        @RemoteCall(CHANNEL)
        public void service(RemoteCall.Caller caller, Custom custom) {
            if (request.equals(custom.payload)) {
                caller.result(new Custom(response));
            } else {
                caller.failure("failed");
            }
        }
    }

    public static class Custom {
        public final String payload;

        public Custom(String payload) {
            this.payload = payload;
        }
    }

    private class CustomConvertor implements JSON.Convertor {
        @Override
        public void toJSON(Object obj, JSON.Output out) {
            Custom custom = (Custom)obj;
            out.add("payload", custom.payload);
        }

        @Override
        public Object fromJSON(Map object) {
            return new Custom((String)object.get("payload"));
        }
    }
}
