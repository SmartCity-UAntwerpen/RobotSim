package be.uantwerpen.rc.models.sim;

import be.uantwerpen.rc.models.sim.deployer.Log;
import be.uantwerpen.rc.services.SimCoresService;
import be.uantwerpen.rc.services.sockets.SimSocketService;
import be.uantwerpen.rc.tools.smartcar.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Thomas on 5/05/2017.
 */
// Class for simulated robots
public class SimCar extends SimVehicle
{
    private SimCore carCore;
    private SimSocketService taskSocketService;
    private SimSocketService eventSocketService;

    public SimCar()
    {
        super("bot", -1, 70);

        this.taskSocketService = new SimSocketService();
        this.eventSocketService = new SimSocketService();
        this.type = "car";
        this.carCore = null;
    }

    @Override
    public boolean parseProperty(String property, String value) throws Exception
    {
        if(super.parseProperty(property, value))
        {
            return true;
        }

        switch(property.toLowerCase().trim())
        {
            default:
                return false;
        }
    }

    @Override
    public boolean parseProperty(String property) throws Exception
    {
        if(super.parseProperty(property))
        {
            return true;
        }

        switch(property.toLowerCase().trim())
        {
            default:
                return false;
        }
    }

    @Override
    protected void simulationProcess()
    {
        Thread commandSocketServiceThread = new Thread(this.taskSocketService);
        Thread eventSocketServiceThread = new Thread(this.eventSocketService);
        commandSocketServiceThread.start();
        eventSocketServiceThread.start();

        //Wait for server sockets to initialise
        while((this.taskSocketService.getListeningPort() == 0 || this.eventSocketService.getListeningPort() == 0) && this.isRunning());

        List<String> coreArguments = new ArrayList<String>();

        //Create core process arguments
        //Setup ports to simulated C-Core
        coreArguments.add("-Dcar.ccore.taskport=" + this.taskSocketService.getListeningPort());
        coreArguments.add("-Dcar.ccore.eventport=" + this.eventSocketService.getListeningPort());
        //Select random free port
        coreArguments.add("-Dserver.port=0");
        coreArguments.add("-Dsc.core.ip=143.129.39.151");
        coreArguments.add("-Dsc.core.port=1994");

        if(this.carCore == null)
        {
            this.carCore = SimCoresService.getSimulationCore(this.type);
        }

        if(this.carCore != null)
        {
            this.carCore.start(coreArguments);
        }
        else
        {
            //No core available
            Log.logSevere("SIMCAR", "Could not run Core for Car simulation!");

            this.stop();

            return;
        }

        //Simulation process of SimCar
        this.simulateCar();

        //Stop simulation
        this.carCore.stop();

        commandSocketServiceThread.interrupt();
        eventSocketServiceThread.interrupt();

        //Wait for socket service to terminate
        while(commandSocketServiceThread.isAlive() || eventSocketServiceThread.isAlive());
    }

    private void simulateCar()
    {
        SmartCar carSimulation = new SmartCar(this.name, this.simSpeed, this.serverCoreIP, this.serverCorePort);

        long lastSimulationTime = System.currentTimeMillis();

        //Initialise simulation
        if(!carSimulation.initSimulation(this.startPoint))
        {
            Log.logSevere("SIMCAR", "Could not initialise SmartCar simulation!");
            Log.logSevere("SIMCAR", "Simulation will abort...");

            this.running = false;
        }

        Log.logInfo("SIMCAR", "SmartCar simulation started.");

        while(this.isRunning())
        {
            //Calculated simulation time
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - lastSimulationTime;
            lastSimulationTime = currentTime;

            //Verify sockets
            carSimulation.checkConnections(taskSocketService, eventSocketService);

            //Update simulation
            carSimulation.updateSimulation(elapsedTime);

            try
            {
                //Sleep simulation for 10 ms (simulation resolution > 10 ms)
                Thread.sleep(10);
            }
            catch(Exception e)
            {
                //Thread is interrupted
            }
        }

        if(!carSimulation.stopSimulation())
        {
            Log.logSevere("SIMCAR", "Simulation layer is not stopped properly!");
        }
    }

    @Override
    public boolean printProperty(String property)
    {
        if(super.printProperty(property))
        {
            return true;
        }

        switch(property.toLowerCase().trim())
        {
            default:
                return false;
        }
    }
}