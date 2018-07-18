package com.github.petelin.esplugin.plugin;

import com.github.petelin.esplugin.script.MyScriptEngine;
import org.apache.logging.log4j.LogManager;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;

import java.util.Arrays;
import java.util.Collection;


public class VectorScoringPlugin extends Plugin implements ScriptPlugin {
    private final static org.apache.logging.log4j.Logger logger = LogManager.getLogger(VectorScoringPlugin.class);


    @Override
    public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
        logger.info("contexts : {} ", Arrays.toString(contexts.toArray()));
        return new MyScriptEngine();
    }

}