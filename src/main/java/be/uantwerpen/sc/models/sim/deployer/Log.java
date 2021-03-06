package be.uantwerpen.sc.models.sim.deployer;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;

/**
 * Logger class
 */
public class Log {

    private static java.util.logging.Logger logging = null;

    public Log(Class t, Level level){
        logging = java.util.logging.Logger.getLogger(t.getName());
        setLogger(level);
    }

    public static void logInfo(String category, String message){
        logging.info("[" + category + "] " + message);
    }

    public static void logWarning(String category, String message){
        logging.warning("[" + category + "] " + message);
    }

    public static void logSevere(String category, String message){
        logging.severe("[" + category + "] " + message);
    }

    public static void logConfig(String category, String message){
        logging.config("[" + category + "] " + message);
    }

    private void setLogger(Level level){
        Handler handlerObj = new ConsoleHandler();
        handlerObj.setLevel(Level.ALL);
        LogFormatter logFormatter = new LogFormatter();
        handlerObj.setFormatter(logFormatter);
        logging.addHandler(handlerObj);
        logging.setLevel(Level.ALL);
        logging.setUseParentHandlers(false);
        logging.setLevel(level);
    }
}
