package com.moqui.ssr;

import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.moqui.context.AuthenticationRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.*;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ReactRender {
    private final static Logger logger = LoggerFactory.getLogger(ReactRender.class);

    private React react;

    private ScriptContext sc;
    private Object html;
    private Object storeState;
    private Object error;
    private boolean promiseResolved;
    private final Object promiseLock = new Object();

    private Map<String, CompiledScript> compiledScriptMap;
    private NashornScriptEngine nashornEngine;
    private Bindings initialBindings;

    private Consumer<Object> fnResolve = object -> {
        synchronized (promiseLock) {
            Map result = (Map) object;
            html = result.get("html");
            storeState = result.get("state");
            error = null;
            promiseResolved = true;
        }
    };

    private Consumer<Object> fnReject = object -> {
        synchronized (promiseLock) {
            error = object;
            html = null;
            storeState = null;
            promiseResolved = true;
            logger.warn("fnReject error:\n" + String.valueOf(error));
        }
    };

    public ReactRender(React react, Map<String, CompiledScript> compiledScriptMap,
                       NashornScriptEngine nashornEngine, Bindings initialBindings) {
        this.react = react;
        this.nashornEngine = nashornEngine;
        this.compiledScriptMap = compiledScriptMap;
        this.initialBindings = initialBindings;

        initializeScriptContext();
    }

    private void initializeScriptContext() {
        sc = new SimpleScriptContext();
        synchronized (this.nashornEngine) {
//            logger.warn("========= initializeScriptContext for session begin " +
//                    react.getExecutionContext().getWeb().getRequest().getSession().getId() +
//                    " in thread " + Thread.currentThread().getName());
            sc.setBindings(nashornEngine.createBindings(), ScriptContext.ENGINE_SCOPE);
            sc.getBindings(ScriptContext.ENGINE_SCOPE).putAll(initialBindings);

            sc.setAttribute("__REQ_URL__", "/", ScriptContext.ENGINE_SCOPE);
            try {
                for (Map.Entry<String, CompiledScript> entry : compiledScriptMap.entrySet()) {
                    entry.getValue().eval(sc);
                }
            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }

//            logger.warn("========= initializeScriptContext for session end " +
//                    react.getExecutionContext().getWeb().getRequest().getSession().getId() +
//                    " in thread " + Thread.currentThread().getName());
        }
    }

    public Map<String, Object> render(HttpServletRequest request, Map<String, CompiledScript> compiledScriptMap,
                                      int jsWaitTimeout, int jsWaitInterval) {
        Map<String, Object> result = new HashMap<>(2);
        result.put("html", null);
        result.put("state", null);
        if (sc == null) initializeScriptContext();
        try {
            String locationUrl = getUrlLocation(request);
            sc.setAttribute("__REQ_URL__", locationUrl, ScriptContext.ENGINE_SCOPE);
            sc.setAttribute("__HTTP_SERVLET_REQUEST__", react.getExecutionContext().getWeb().getRequest(), ScriptContext.ENGINE_SCOPE);
            try {
                for (Map.Entry<String, CompiledScript> entry : compiledScriptMap.entrySet()) {
                    entry.getValue().eval(sc);
                }
            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }

            promiseResolved = false;

            ScriptObjectMirror app = (ScriptObjectMirror) sc.getBindings(ScriptContext.ENGINE_SCOPE).get("newApp");
            ScriptObjectMirror promise = (ScriptObjectMirror) app.callMember("render");
            promise.callMember("then", fnResolve, fnReject);

            ScriptObjectMirror nashornEventLoop = (ScriptObjectMirror) sc.getBindings(ScriptContext.ENGINE_SCOPE).get("nashornEventLoop");
            nashornEventLoop.callMember("process");

            int i = 0;
            int interval = jsWaitInterval;
            int totalWaitTime = 0;
            while (!promiseResolved && totalWaitTime < jsWaitTimeout) {
                nashornEventLoop.callMember("process");
                Thread.sleep(interval);
                totalWaitTime = totalWaitTime + interval;
                interval = interval * 2;
                i = i + 1;
            }

            if (!promiseResolved) {
                nashornEventLoop.callMember("reset");
                logger.warn(locationUrl + " timeout session " +
                        react.getExecutionContext().getWeb().getRequest().getSession().getId() +
                        " in thread " + Thread.currentThread().getName());
            }
            result.put("html", html);
            result.put("state", storeState);

            boolean status401 = false;
            if (Boolean.TRUE.equals(app.getMember("status401"))) status401 = true;
            if (status401) throw new AuthenticationRequiredException("During javascript execution, 401 response is returned");

        } catch (AuthenticationRequiredException e) {
          throw e;
        } catch (Exception e) {
            throw new IllegalStateException("failed to render react session " +
                    react.getExecutionContext().getWeb().getRequest().getSession().getId() +
                    " in thread " + Thread.currentThread().getName(), e);
        } finally {
            if (!promiseResolved) sc = null;
            resetRender();
        }

        return result;
    }

    private void resetRender() {
        html = null;
        storeState = null;
        error = null;
        promiseResolved = true;
    }

    private static String getUrlLocation(HttpServletRequest request) {
        StringBuilder requestUrl = new StringBuilder();
        requestUrl.append(request.getRequestURI());
        if (request.getQueryString() != null && request.getQueryString().length() > 0) requestUrl.append("?" + request.getQueryString());
        return requestUrl.toString();
    }

}
