package route.interfaces;

import route.main.Main;


public abstract class OptionsManager {

    public enum StartingStage {NET, PLACE};

    protected Logger logger;

    private Options mainOptions;


    protected OptionsManager(Logger logger) {
        this.logger = logger;

        this.mainOptions = new Options(this.logger);
        Main.initOptionList(this.mainOptions);
    }

    public Logger getLogger() {
        return this.logger;
    }

    public Options getMainOptions() {
        return this.mainOptions;
    }


}
