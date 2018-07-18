package com.github.petelin.esplugin.script;

import org.apache.logging.log4j.LogManager;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.elasticsearch.index.similarity.ScriptedSimilarity;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.lookup.FieldLookup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MyScriptEngine implements ScriptEngine {
    private final static org.apache.logging.log4j.Logger logger = LogManager.getLogger(MyScriptEngine.class);

    final static public String SCRIPT_NAME = "payload_vector_score";
    // the field containing the vectors to be scored against
    String field = null;
    // indices for the query vector
    List<String> index = null;
    // vector for the query vector
    List<Double> vector = null;
    // whether to score cosine similarity (true) or dot product (false)
    boolean cosine = false;
    double queryVectorNorm = 0;

    @Override
    public String getType() {
        return "expert_scripts"; //for lang
    }

    @Override
    public <T> T compile(String scriptName, String scriptSource, ScriptContext<T> context, Map<String, String> params) {
        if (context.equals(SearchScript.CONTEXT) == false) {
            throw new IllegalArgumentException(getType() + " scripts cannot be used for context [" + context.name + "]");
        }
        // we use the script "source" as the script identifier
        if ("payload_vector_score".equals(scriptSource)) {
            SearchScript.Factory factory = (p, lookup) -> new SearchScript.LeafFactory() {
                {
                    if (p.containsKey("field") == false) {
                        throw new IllegalArgumentException("Missing parameter [field]");
                    }
                    if (p.containsKey("vector") == false) {
                        throw new IllegalArgumentException("Missing parameter [vector]");
                    }
                    if (p.containsKey("cosine") == false) {
                        throw new IllegalArgumentException("Missing parameter [cosine]");
                    }

                    field = p.get("field").toString();


                    vector = (List<Double>) p.get("vector");
                    // init index
                    index = new ArrayList<>(vector.size());
                    for (int i = 0; i < vector.size(); i++) {
                        index.add(String.valueOf(i));
                    }
                    if (vector.size() != index.size()) {
                        throw new IllegalArgumentException("cannot initialize " + SCRIPT_NAME + ": index and vector array must have same length!");
                    }


                    Object cosineParam = p.get("cosine");
                    if (cosineParam != null) {
                        cosine = (boolean)cosineParam;
                    }
                    logger.info("from out param is : " + field + "" + vector + cosine);

                    if (cosine) {
                        // compute query vector norm once
                        for (double v : vector) {
                            queryVectorNorm += Math.pow(v, 2.0);
                        }
                    }
                }

                @Override
                public SearchScript newInstance(LeafReaderContext context) throws IOException {

                    return new SearchScript(p, lookup, context) {
                        @Override
                        public double runAsDouble() {
                            float score = 0;
                            // first, get the ShardTerms object for the field.
                            String indexField = (String) getLeafLookup().source().get(field);
                            String[] items = indexField.split(" ");
                            ArrayList<Float> indexFields = new ArrayList<>();
                            for (String item : items) {
                                indexFields.add(Float.parseFloat(item.split("\\|")[1]));
                            }
                            double docVectorNorm = 0.0f;
                            for (int i = 0; i < index.size(); i++) {
                                // get the vector value stored in the term payload
                                float payload = indexFields.get(i);

                                if (cosine) {
                                    // doc vector norm
                                    docVectorNorm += Math.pow(payload, 2.0);
                                }
                                // dot product
                                score += payload * vector.get(i);
                            }
                            if (cosine) {
                                // cosine similarity score
                                if (docVectorNorm == 0 || queryVectorNorm == 0) return 0f;
                                return score / (Math.sqrt(docVectorNorm) * Math.sqrt(queryVectorNorm));
                            } else {
                                // dot product score
                                return score;
                            }

                        }
                    };
                }
                @Override
                public boolean needs_score() {
                    return true;
                }
            };
            return context.factoryClazz.cast(factory);
        }
        throw new IllegalArgumentException("Unknown script name " + scriptSource);
    }

    @Override
    public void close() {
        // optionally close resources
    }
}