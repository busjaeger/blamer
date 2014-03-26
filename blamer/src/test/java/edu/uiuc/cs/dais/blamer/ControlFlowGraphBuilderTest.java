package edu.uiuc.cs.dais.blamer;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

import org.junit.Test;

import edu.uiuc.cs.dais.cfg.ControlFlowGraphBuilder;

public class ControlFlowGraphBuilderTest {

    @Test
    public void test() {
        final ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder();
        final String[] paths = { getFile("test1/A.java").getAbsolutePath() };
        builder.build(paths);
    }

    File getFile(String path) {
        final URL url = getClass().getClassLoader().getResource(path);
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("ClassLoader returned invalid url " + url, e);
        }
    }

}
