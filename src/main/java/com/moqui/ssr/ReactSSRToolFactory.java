package com.moqui.ssr;


import org.moqui.context.ExecutionContextFactory;
import org.moqui.context.ToolFactory;
import org.moqui.resource.ResourceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ReactSSRToolFactory implements ToolFactory<React> {

    private final static Logger logger = LoggerFactory.getLogger(ReactSSRToolFactory.class);
    private final static String TOOL_NAME = "ReactSSR";

    private ExecutionContextFactory ecf = null;

    private final Map<String, React> reactMap = new HashMap<>();

    /**
     * Default empty constructor
     */
    ReactSSRToolFactory() {
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public void init(ExecutionContextFactory ecf) {
        this.ecf = ecf;
        logger.info("ReactSSRToolFactory Initialized");
    }

    @Override
    public void preFacadeInit(ExecutionContextFactory ecf) {
    }

    /*
       paramters:
        String reactAppName
        Map<String, ResourceReference> appJsFileMap
     */
    @Override
    public React getInstance(Object... parameters) {
        if (parameters.length != 2)
            throw new IllegalArgumentException("ReactSSRToolFactory getInstance needs 2 parameters");
        String reactAppName = (String) parameters[0];
        React react = reactMap.get(reactAppName);
        if (react == null) {
            synchronized (reactMap) {
                Map<String, ResourceReference> appJsFileMap = (Map<String, ResourceReference>) parameters[1];

                react = new React(ecf, appJsFileMap);
                reactMap.put(reactAppName, react);
            }
        }
        return react;
    }

    @Override
    public void destroy() {

    }

    public ExecutionContextFactory getEcf() {
        return ecf;
    }
}
