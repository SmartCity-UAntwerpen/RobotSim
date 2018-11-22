package be.uantwerpen.sc.services;

import be.uantwerpen.sc.models.sim.SimBot;
import be.uantwerpen.sc.models.sim.SimCar;
import be.uantwerpen.sc.models.sim.deployer.Log;
import be.uantwerpen.sc.models.sim.deployer.TCPListener;
import be.uantwerpen.sc.models.sim.deployer.TCPUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
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
public class SimDeployerService implements TCPListener {

    /**
     * Port on which the simulation will accept TODO requests
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

    /**
     * Map of all the simulated vehicles and their simulation identifiers
     */
    private static HashMap<Long, SimBot> simulatedVehicles = new HashMap<>();

    @Autowired
    private Environment environment;

    public SimDeployerService() throws IOException {
    }

    /**
     * Start sequence, attempts to start TCPUtils and thus tries to open sockets
     */
    public void start() {
        log = new Log(this.getClass(), level);
        try {
            tcpUtils = new TCPUtils(simPort, this,true);
        } catch (IOException e) {
            e.printStackTrace();
            Log.logSevere("SIMDEPLOYER", "SimDeployer could not be started. TCP IO-exception.");
        }
        tcpUtils.start();
        Log.logInfo("SIMDEPLOYER", "SimDeployer has been started.");
    }

    /**
     * Parses incoming TCP message into the corresponding vehicle command
     * @param message Message received
     * @return "ACK" or "NACK", to be parsed by the other server
     * @throws IOException
     */
    @Override
    public String parseTCP(String message) throws IOException {
        boolean result = false;
        if (message.matches("create\\s[0-9]+")) {
            result = createVehicle(Long.parseLong(message.replaceAll("\\D+", "")));
        } else if (message.matches("run\\s[0-9]+")) {
            result = startupVehicle(Long.parseLong(message.replaceAll("\\D+", "")));
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
                    Log.logInfo("SIMDEPLOYER", "Simulated Vehicle with simulation ID " + simulationID + " property set.");
                    return true;
                }
                else {
                    Log.logInfo("SIMDEPLOYER", "Simulated Vehicle with simulation ID " + simulationID + " property could not be set.");
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } else {
            Log.logWarning("SIMDEPLOYER", "Cannot set property of vehicle with simulation ID " + simulationID + ". It does not exist.");
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
            SimCar newCar = new SimCar();
            simulatedVehicles.put(simulationID, newCar);
            Log.logInfo("SIMDEPLOYER", "New simulated vehicle registered with simulation ID " + simulationID + ".");
            return true;
        } else {
            Log.logWarning("SIMDEPLOYER", "Cannot create vehicle with simulation ID " + simulationID + ". It already exists.");
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
                Log.logInfo("SIMDEPLOYER", "Vehicle with ID " + simulationID + " Stopped.");
                return true;
            }
            else {
                Log.logInfo("SIMDEPLOYER", "Vehicle with ID " + simulationID + " cannot be stopped.");
                return false;
            }
        } else {
            Log.logWarning("SIMDEPLOYER", "Cannot stop vehicle with simulation ID " + simulationID + ". It does not exist.");
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
                Log.logInfo("SIMDEPLOYER", "Vehicle with ID " + simulationID + " killed.");
                return true;
            }
            Log.logInfo("SIMDEPLOYER", "Vehicle with ID " + simulationID + " cannot be killed.");
            return true;
        } else {
            Log.logWarning("SIMDEPLOYER", "Cannot kill vehicle with simulation ID " + simulationID + ". It does not exist.");
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
                Log.logInfo("SIMDEPLOYER", "Restarted vehicle with simulation ID " + simulationID + ".");
                return true;
            } else {
                Log.logWarning("SIMDEPLOYER", "Cannot restart vehicle with simulation ID " + simulationID + ". It isn't started.");
                return false;
            }
        } else {
            Log.logWarning("SIMDEPLOYER", "Cannot restart vehicle with simulation ID " + simulationID + ". It does not exist.");
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
                    Log.logInfo("SIMDEPLOYER", "Simulated Vehicle with simulation ID " + simulationID + " started.");
                    return true;
                } else {
                    Log.logWarning("SIMDEPLOYER", "Cannot start vehicle with simulation ID " + simulationID + ". It didn't have a starting point set.");
                    return false;
                }
            } else {
                Log.logWarning("SIMDEPLOYER", "Cannot start vehicle with simulation ID " + simulationID + ". It didn't have a starting point set.");
                return false;
            }
        } else {
            Log.logWarning("SIMDEPLOYER", "Cannot start vehicle with simulation ID " + simulationID + ". It does not exist.");
            return false;
        }
    }
}