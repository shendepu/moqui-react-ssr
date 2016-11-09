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
        String basePath
        Map<String, ResourceReference> appJsFileMap
        Map<String, Object> optionMap [optional]
            - int jsTimeout: ms
     */
    @Override
    public React getInstance(Object... parameters) {
        if (parameters.length < 3)
            throw new IllegalArgumentException("ReactSSRToolFactory getInstance must have parameters of [reactAppName, basePath, appJsFileMap]");
        String reactAppName = (String) parameters[0];
        React react = reactMap.get(reactAppName);
        if (react == null) {
            synchronized (reactMap) {
                String basePath = (String) parameters[1];
                Map<String, ResourceReference> appJsFileMap = (Map) parameters[2];
                Map<String, Object> optionMap;
                if (parameters.length > 3) optionMap = (Map) parameters[3];
                else optionMap = new HashMap<>();

                react = new React(ecf, basePath, appJsFileMap, optionMap);
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
