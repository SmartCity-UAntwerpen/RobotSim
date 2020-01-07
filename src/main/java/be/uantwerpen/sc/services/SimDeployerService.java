package be.uantwerpen.sc.services;

import be.uantwerpen.sc.Messages.ServerMessage;
import be.uantwerpen.sc.Messages.WorkerJob;
import be.uantwerpen.sc.Messages.WorkerMessage;
import be.uantwerpen.sc.models.sim.SimBot;
import be.uantwerpen.sc.models.sim.SimCar;
import be.uantwerpen.sc.models.sim.WebSocket.SocketCallback;
import be.uantwerpen.sc.models.sim.WebSocket.WorkerClient;
import be.uantwerpen.sc.models.sim.deployer.Log;
import be.uantwerpen.sc.models.sim.deployer.TCPListener;
import be.uantwerpen.sc.models.sim.deployer.TCPUtils;
import be.uantwerpen.sc.models.sim.deployer.callBackTCPMessages;
//import com.sun.xml.internal.bind.v2.TODO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Level;

/**
 * @author Thomas on 3/06/2017.
 * @comments Reinout on 13/01/2018
 * Converts incoming TCP packets into commands for the vehicle
 */
@Service
public class SimDeployerService implements TCPListener, SocketCallback {

    private static final Logger logger = LoggerFactory.getLogger(SimDeployerService.class);
    /**
     * Port on which the simulation will accept to do requests
     */
    @Value("#{new Integer(${simPort})}")
    private int simPort;

    /**
     * Logger used for logging info, warnings etc
     */
    private static Log log;

    /**
     * Logging level
     */
    private Level level = Level.CONFIG;

    /**
     * TODO
     */
    private TCPUtils tcpUtils;
    private WorkerClient workerClient;
    /**
     * Map of all the simulated vehicles and their simulation identifiers
     */
    private static HashMap<Long, SimBot> simulatedVehicles = new HashMap<>();

    /**
     * ID of the Worker given by the Server
     */
    private long ID = 0L;
    @Autowired
    private ApplicationContext cont;

    @Autowired
    private Environment environment;

    @Autowired
    private SimFactory simFactory;

    //The stompsession with the server
    private StompSession session;

    public SimDeployerService() throws IOException {
    }

    /**
     * Start sequence, attempts to start TCPUtils and thus tries to open sockets
     */
    public void start() {
        log = new Log(this.getClass(), level);
        /*try {
            tcpUtils = new TCPUtils(simPort, this,true);
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("SimDeployer could not be started. TCP IO-exception.");
            //Log.logSevere("SIMDEPLOYER", "SimDeployer could not be started. TCP IO-exception.");
        }
        tcpUtils.start();*/
        //To implement in docker toolbox: use ws://192.168.99.1:1394/worker before generating jar
         workerClient = new WorkerClient("ws://localhost:1394/worker",this,0,0,simulatedVehicles.size());
        workerClient.start();
        logger.info("SimDeployer has been started.");
        //Log.logInfo("SIMDEPLOYER", "SimDeployer has been started.");
    }

    /**
     * Parses incoming TCP message into the corresponding vehicle command
     * @param message Message received
     * @return "ACK" or "NACK", to be parsed by the other server
     * @throws IOException
     */
    //TODO
    //https://docs.spring.io/spring/docs/current/spring-framework-reference/web.html#websocket-stomp-server-config
    @Override
    public String parseTCP(String message) throws IOException {
        boolean result = false;
        if (message.matches("create\\s[0-9]+")) {
            result = createVehicle(Long.parseLong(message.replaceAll("\\D+", "")));
        } else if (message.matches("run\\s[0-9]+")) {
            result = startupVehicle(Long.parseLong(message.replaceAll("\\D+", "")));
        } else if (message.matches("ping")) {
            return "PONG";
        } else if (message.matches("stop\\s[0-9]+")) {
            result = stopVehicle(Long.parseLong(message.replaceAll("\\D+", "")));
        } else if (message.matches("kill\\s[0-9]+")) {
            result = killVehicle(Long.parseLong(message.replaceAll("\\D+", "")));
        } else if (message.matches("restart\\s[0-9]+")) {
            result = restartVehicle(Long.parseLong(message.replaceAll("\\D+", "")));
        } else if (message.matches("set\\s[0-9]+\\s\\w+\\s\\w+")) {
            String[] splitString = message.split("\\s+");
            Long simulationID = Long.parseLong(splitString[1]);
            String parameter = splitString[2];
            String argument = splitString[3];
            result = setVehicle(simulationID,parameter,argument);
        }
        if(result){
            return "ACK";
        }
        else{
            return "NACK";
        }
    }

    @Override
    public boolean setSession(StompSession session) {
        this.session = session;
        if(session == null){
            return false;
        }
        return true;
    }

    @Override
    public boolean parseMessage(ServerMessage message) {
        boolean result = false;
        if(message.getWorkerID() == ID || message.getJob() == WorkerJob.CONNECTION){
            if (message.getJob() == WorkerJob.BOT) {
                System.out.println("creating bot " + message.getBotID());
                result = createVehicle(message.getBotID());
            } else if (message.getJob() == WorkerJob.START) {
                System.out.println("starting bot " + message.getBotID());
                result = startupVehicle(message.getBotID());
            } else if (message.getJob() == WorkerJob.STOP) {
                System.out.println("stopping bot " + message.getBotID());
                result = stopVehicle(message.getBotID());
            } else if (message.getJob() == WorkerJob.KILL) {
                System.out.println("shutting down bot " + message.getBotID());
                result = killVehicle(message.getBotID());
            } else if (message.getJob() == WorkerJob.RESTART) {
                System.out.println("restarting bot " + message.getBotID());
                result = restartVehicle(message.getBotID());
            } else if (message.getJob() == WorkerJob.CONNECTION) {
                if(message.getArguments().equalsIgnoreCase("SHUTDOWN") && ID == message.getWorkerID()){
                    System.out.println("Shutting down");
                    shutdownApp();
                }else if(ID == 0L){ //#TODO What if shutdown other worker while connecting?
                    System.out.println("Connecting...");
                    System.out.println("Setting workerID " + message.getWorkerID());
                    result = setServerID(message.getWorkerID());
                }
            }else if (message.getJob() == WorkerJob.SET) {
                System.out.println("Setting parameters:  " +message.getArguments() + " of bot: " + + message.getBotID());
                String[] splitString = message.getArguments().split("\\s+");
                Long simulationID = new Long(message.getBotID()) ;
                String parameter = splitString[0];
                String argument = splitString[1];
                result = setVehicle(simulationID,parameter,argument);
            } else{
                System.err.println("message_error");
            }
            if(result){
                System.out.println("Result OK");
            }else if(message.getJob() != WorkerJob.CONNECTION){
                System.err.println("Problem Occured " + message.getJob());
            }
            if(result && message.getJob() != WorkerJob.CONNECTION){
                System.out.println("Ack");
                session.send("/SimCity/Robot/topic/answers",new ServerMessage(this.ID,message.getJob(),message.getBotID(),"OK"));
            }else if (!result && message.getWorkerID() == this.ID && message.getJob() != WorkerJob.CONNECTION){
                session.send("/SimCity/Robot/topic/answers",new ServerMessage(this.ID,message.getJob(),message.getBotID(),"NOK"));
            }
        }

        return result;
    }

    //@Override

    //This method handles the TCP traffic from the TCPClient
    //@Override
    /*public String sendMessage(String message) throws IOException{
        if(message.equalsIgnoreCase("BOOTING")){
            return ("RequestID:0");
        }else if (message.equalsIgnoreCase("RUNNING")){
            return ("Alive:"+this.ID+"Bots:"+simulatedVehicles.size());
        }else if (message.equalsIgnoreCase("SHUTDOWN")){
            return ("ShuttingDown:"+this.ID);
        }
        return "UNKNOWN";
    }*/

    /*@Override
    public Boolean executeMessage(String message){
        String job = message.split(":")[0];
        String[] totaljob = message.split(":");
        if(job.equalsIgnoreCase("WorkerID")){
           ID = Integer.parseInt(totaljob[1]);
            return true;
        }else if (job.equalsIgnoreCase("BOT")){
           createVehicle(Long.parseLong(totaljob[1]));
            return true;
        }else if (job.equalsIgnoreCase("KILL")){
            killVehicle(Long.parseLong(totaljob[1]));
            return true;
        }else if (job.equalsIgnoreCase("STOP")){
            stopVehicle(Long.parseLong(totaljob[1]));
            return true;
        }else if (job.equalsIgnoreCase("RESTART")){
            restartVehicle(Long.parseLong(totaljob[1]));
            return true;
        }else if (job.equalsIgnoreCase("SET")){
            setVehicle(Long.parseLong(totaljob[1]),totaljob[2],totaljob[3]);
            return true;
        }else if (job.equalsIgnoreCase("RECEIVED")){
            return true;
        }else{
            return false;
        }

    }*/
    /**
     * Attempts to update a given property of a vehicle
     * @param simulationID ID of vehicle
     * @param parameter Property to update
     * @param argument Value to give to property
     * @return success
     */
    private boolean setVehicle(long simulationID, String parameter, String argument) {
        if (simulatedVehicles.containsKey(simulationID)) {
            try {
                if (simulatedVehicles.get(simulationID).parseProperty(parameter, argument)) {
                    logger.info("Simulated Vehicle with simulation ID " + simulationID + " property set.");
                    //Log.logInfo("SIMDEPLOYER", "Simulated Vehicle with simulation ID " + simulationID + " property set.");
                    return true;
                }
                else {
                    logger.info("Simulated Vehicle with simulation ID " + simulationID + " property could not be set.");
                    //Log.logInfo("SIMDEPLOYER", "Simulated Vehicle with simulation ID " + simulationID + " property could not be set.");
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } else {
            logger.warn("Cannot set property of vehicle with simulation ID " + simulationID + ". It does not exist.");
            //Log.logWarning("SIMDEPLOYER", "Cannot set property of vehicle with simulation ID " + simulationID + ". It does not exist.");
            return false;
        }
    }

    /**
     * Attempts to create & add a vehicle with given simulation ID
     * @param simulationID ID of the vehicle
     * @return Success
     */
    private boolean createVehicle(long simulationID){
        if (!simulatedVehicles.containsKey(simulationID)) {
            SimCar newCar = simFactory.createSimCar(simulationID);
            simulatedVehicles.put(simulationID, newCar);
            logger.info("New simulated vehicle registered with simulation ID " + simulationID + ".");
            //Log.logInfo("SIMDEPLOYER", "New simulated vehicle registered with simulation ID " + simulationID + ".");
            return true;
        } else {
            logger.warn("Cannot create vehicle with simulation ID " + simulationID + ". It already exists.");
            //Log.logWarning("SIMDEPLOYER", "Cannot create vehicle with simulation ID " + simulationID + ". It already exists.");
            return false;
        }
    }

    /**
     * Attempts to stop a vehicle with given simulation ID
     * @param simulationID ID of the vehicle
     * @return Success
     */
    private boolean stopVehicle(long simulationID) {
        if (simulatedVehicles.containsKey(simulationID)) {
            if (simulatedVehicles.get(simulationID).stop()) {
                logger.info("Vehicle with ID " + simulationID + " Stopped.");
                //Log.logInfo("SIMDEPLOYER", "Vehicle with ID " + simulationID + " Stopped.");
                return true;
            }
            else {
                logger.info("Vehicle with ID " + simulationID + " cannot be stopped.");
                //Log.logInfo("SIMDEPLOYER", "Vehicle with ID " + simulationID + " cannot be stopped.");
                return false;
            }
        } else {
            logger.warn("Cannot stop vehicle with simulation ID " + simulationID + ". It does not exist.");
            //Log.logWarning("SIMDEPLOYER", "Cannot stop vehicle with simulation ID " + simulationID + ". It does not exist.");
            return false;
        }
    }

    /**
     * Attempts to kill a vehicle with given simulation ID
     * This also removes it from the list of vehicles
     * @param simulationID ID of vehicle
     * @return success or not
     */
    private boolean killVehicle(long simulationID) {
        if (simulatedVehicles.containsKey(simulationID)) {
            if (simulatedVehicles.get(simulationID).remove()) {
                simulatedVehicles.remove(simulationID);
                //Log.logInfo("SIMDEPLOYER", "Vehicle with ID " + simulationID + " killed.");
                logger.info("Vehicle with ID " + simulationID + " killed.");
                return true;
            }
            logger.info("Vehicle with ID " + simulationID + " cannot be killed.");
            //Log.logInfo("SIMDEPLOYER", "Vehicle with ID " + simulationID + " cannot be killed.");
            return true;
        } else {
            logger.warn("Cannot kill vehicle with simulation ID " + simulationID + ". It does not exist.");
            //Log.logWarning("SIMDEPLOYER", "Cannot kill vehicle with simulation ID " + simulationID + ". It does not exist.");
            return false;
        }
    }

    /**
     * Attempts to restart a vehicle with given simulation ID
     * @param simulationID ID of vehicle
     * @return Success
     */
    private boolean restartVehicle(long simulationID){
        if (simulatedVehicles.containsKey(simulationID)) {
            if (simulatedVehicles.get(simulationID).restart()) {
                //Log.logInfo("SIMDEPLOYER", "Restarted vehicle with simulation ID " + simulationID + ".");
                logger.info("Restarted vehicle with simulation ID " + simulationID + ".");
                return true;
            } else {
                logger.warn("Cannot restart vehicle with simulation ID " + simulationID + ". It isn't started.");
                //Log.logWarning("SIMDEPLOYER", "Cannot restart vehicle with simulation ID " + simulationID + ". It isn't started.");
                return false;
            }
        } else {
            logger.warn("Cannot restart vehicle with simulation ID " + simulationID + ". It does not exist.");
            //Log.logWarning("SIMDEPLOYER", "Cannot restart vehicle with simulation ID " + simulationID + ". It does not exist.");
            return false;
        }
    }

    /**
     * Attempts to start a vehicle with given simulation ID
     * @param simulationID ID of the vehicle to start
     * @return Success
     */
    private boolean startupVehicle(long simulationID){
        if (simulatedVehicles.containsKey(simulationID)) {
            if(simulatedVehicles.get(simulationID).getStartPoint() != -1) {
                if (simulatedVehicles.get(simulationID).start()) {
                    logger.info("Simulated Vehicle with simulation ID " + simulationID + " started.");
                    //Log.logInfo("SIMDEPLOYER", "Simulated Vehicle with simulation ID " + simulationID + " started.");
                    return true;
                } else {
                    logger.warn("Cannot start vehicle with simulation ID " + simulationID + ". It didn't have a starting point set.");
                    //Log.logWarning("SIMDEPLOYER", "Cannot start vehicle with simulation ID " + simulationID + ". It didn't have a starting point set.");
                    return false;
                }
            } else {
                logger.warn("Cannot start vehicle with simulation ID " + simulationID + ". It didn't have a starting point set.");
                //Log.logWarning("SIMDEPLOYER", "Cannot start vehicle with simulation ID " + simulationID + ". It didn't have a starting point set.");
                return false;
            }
        } else {
            logger.warn("Cannot start vehicle with simulation ID " + simulationID + ". It does not exist.");
            //Log.logWarning("SIMDEPLOYER", "Cannot start vehicle with simulation ID " + simulationID + ". It does not exist.");
            return false;
        }
    }
    private boolean setServerID(long id){
        this.ID = id;
        return true;
    }
    //#TODO answer that it has correctly shut down the program sends it last result so could use that. Check if always the case
    private void shutdownApp(){
        workerClient.stopProgram();
    }
}


