package top.lihuu.redis4j;

import ch.vorburger.exec.OutputStreamLogDispatcher;
import ch.vorburger.exec.OutputStreamType;
import org.slf4j.event.Level;

public class MariaDBOutputStreamLogDispatcher extends OutputStreamLogDispatcher {

    /**
     * {@inheritDoc}
     */
    @Override
    public Level dispatch(OutputStreamType type, String line) {
        if (type == OutputStreamType.STDOUT) {
            return Level.INFO;
        }
        if (line.contains("ERROR") || line.contains("error")) {
            return Level.ERROR;
        }
        return Level.INFO;
    }
}
