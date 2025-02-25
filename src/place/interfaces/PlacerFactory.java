package place.interfaces;


import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import place.circuit.Circuit;
import place.circuit.timing.TimingGraphSLL;
import place.placers.Placer;
import place.visual.PlacementVisualizer;

class PlacerFactory {

    private static Map<String, String> placers = new LinkedHashMap<>();
    static {
        PlacerFactory.placers.put("random", "place.placers.random.RandomPlacer");

        PlacerFactory.placers.put("wld_sa", "place.placers.simulatedannealing.SimulatedAnnealingPlacerWLD");
        PlacerFactory.placers.put("td_sa", "place.placers.simulatedannealing.SimulatedAnnealingPlacerTD");

        PlacerFactory.placers.put("wld_gp", "place.placers.analytical.GradientPlacerWLD");
        PlacerFactory.placers.put("td_gp", "place.placers.analytical.GradientPlacerTD");

        PlacerFactory.placers.put("wld_ap", "place.placers.analytical.AnalyticalPlacerWLD");
        PlacerFactory.placers.put("td_ap", "place.placers.analytical.AnalyticalPlacerTD");
    }


    private Logger logger;

    public PlacerFactory(Logger logger) {
        this.logger = logger;
    }

    public Set<String> placers() {
        return PlacerFactory.placers.keySet();
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Placer> getClass(String placerName) throws IllegalArgumentException, ClassNotFoundException {
        String classPath = PlacerFactory.placers.get(placerName);

        if(classPath == null) {
            throw new IllegalArgumentException("Non-existent placer: " + placerName);
        }

        return (Class<? extends Placer>) Class.forName(classPath);
    }

    private <T extends Placer> Constructor<T> getConstructor(Class<T> placerClass) throws NoSuchMethodException, SecurityException {
        return placerClass.getConstructor(Circuit[].class, Options.class, Random.class, Logger.class, PlacementVisualizer[].class, int.class, int.class);
    }

    private <T extends Placer> Constructor<T> getNewConstructor(Class<T> placerClass) throws NoSuchMethodException, SecurityException {
        return placerClass.getConstructor(Circuit[].class, Options.class, Random.class, Logger.class, PlacementVisualizer[].class, int.class, int.class, TimingGraphSLL.class);
    }

    public Options initOptions(String placerName) {

        Options options = new Options(this.logger);

        try {
            Class<? extends Placer> placerClass = this.getClass(placerName);
            Method initOptions = placerClass.getMethod("initOptions", Options.class);

            initOptions.invoke(null, options);

        } catch(IllegalArgumentException | ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException error) {
            this.logger.raise(error);
        }

        return options;
    }



    public Placer newPlacer(String placerName, Circuit[] circuit, Options options, Random random, 
    		PlacementVisualizer[] visualizer, int totalDies, int SLLrows) {
        try {
            Class<? extends Placer> placerClass = this.getClass(placerName);
            Constructor<? extends Placer> placerConstructor = this.getConstructor(placerClass);
            System.out.print("\nThe placer name is " + placerName + "\n" );
            
            return placerConstructor.newInstance(circuit, options, random, this.logger, visualizer, totalDies, SLLrows);

        } catch(IllegalArgumentException | ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | InvocationTargetException error) {
            this.logger.raise(error);
            return null;
        }
    }
    public Placer newPlacer(String placerName, Circuit[] circuit, Options options, Random random, 
    		PlacementVisualizer[] visualizer, int totalDies, int SLLrows, TimingGraphSLL timingGraphSLL) {
        try {
            Class<? extends Placer> placerClass = this.getClass(placerName);
            Constructor<? extends Placer> placerConstructor = this.getNewConstructor(placerClass);
            System.out.print("\nThe placer name is " + placerName + "\n" );
            
            return placerConstructor.newInstance(circuit, options, random, this.logger, visualizer, totalDies, SLLrows, timingGraphSLL);

        } catch(IllegalArgumentException | ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | InvocationTargetException error) {
            this.logger.raise(error);
            return null;
        }
    }
}
