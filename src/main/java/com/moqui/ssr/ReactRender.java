package com.moqui.ssr;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.apache.commons.pool2.ObjectPool;

import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ReactRender {
    private React react;

    private Object html;
    private Object error;
    private boolean promiseResolved;
    private final Object promiseLock = new Object();


    private Consumer<Object> fnResolve = object -> {
        synchronized (promiseLock) {
            html = object;
            error = null;
            promiseResolved = true;
        }
    };

    private Consumer<Object> fnReject = object -> {
        synchronized (promiseLock) {
            error = object;
            html = "";
            promiseResolved = true;
        }
    };

    public ReactRender(React react) {
        this.react = react;
    }

    public Map<String, Object> render(HttpServletRequest request, Map<String, CompiledScript> compiledScriptMap,
                                      int jsWaitRetryTimes, int jsWaitInterval) {
        Map<String, Object> result = new HashMap<>(2);
        result.put("html", null);
        result.put("state", null);

        ObjectPool<ScriptContext> pool = react.getScriptContextPool();
        try {
            ScriptContext sc = pool.borrowObject();
            try {
                String locationUrl = getUrlLocation(request);
                sc.setAttribute("__REQ_URL__", locationUrl, ScriptContext.ENGINE_SCOPE);
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

                int i = 1;
                while (!promiseResolved && i < jsWaitRetryTimes) {
                    i = i + 1;
                    Thread.sleep(jsWaitInterval);
                }

                result.put("html", html);
                result.put("state", app.callMember("getState"));

            } catch (Exception e) {
                pool.invalidateObject(sc);
                sc = null;
            } finally {
                if (null != sc) pool.returnObject(sc);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to render react", e);
        }

        return result;
    }

    private static String getUrlLocation(HttpServletRequest request) {
        StringBuilder requestUrl = new StringBuilder();
        requestUrl.append(request.getRequestURI());
        if (request.getQueryString() != null && request.getQueryString().length() > 0) requestUrl.append("?" + request.getQueryString());
        return requestUrl.toString();
    }

}
