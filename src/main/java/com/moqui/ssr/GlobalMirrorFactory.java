package com.moqui.ssr;

import jdk.nashorn.api.scripting.NashornScriptEngine;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.*;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class GlobalMirrorFactory extends BasePooledObjectFactory<ScriptContext> {
    private final static Logger logger = LoggerFactory.getLogger(GlobalMirrorFactory.class);

    private final NashornScriptEngine nashornEngine;
    private Map<String, CompiledScript> compiledScriptMap;
    private final Bindings initialBindings;

    private Lock lock = new ReentrantLock();

    public GlobalMirrorFactory(NashornScriptEngine nashornEngine, Bindings initialBindings,
                               Map<String, CompiledScript> compiledScriptMap) {
        this.nashornEngine = nashornEngine;
        this.compiledScriptMap = compiledScriptMap;
        this.initialBindings = initialBindings;
    }

    @Override
    public ScriptContext create() throws Exception {
        lock.lock();
        ScriptContext sc = new SimpleScriptContext();
        try {
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
        } finally {
            lock.unlock();
        }
        return sc;
    }

    @Override
    public PooledObject<ScriptContext> wrap(ScriptContext obj) {
        return new DefaultPooledObject<>(obj);
    }
}
