package route.interfaces;

import route.interfaces.Logger.Stream;
import route.util.Pair;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


class CLIOptions extends OptionsManager {

    private List<String> args;


    public CLIOptions(Logger logger) {
        super(logger);
    }

    void parseArguments(String[] argArray) {

//    	System.out.print("The array is " + Arrays.asList(argArray));
        // First create a logger
        this.args = this.createLogger(Arrays.asList(argArray));
        // Check if a help argument is provided
        int helpArgIndex = Math.max(this.args.indexOf("-h"), this.args.indexOf("--help"));
        if(helpArgIndex >= 0) {
            this.printHelp(Stream.OUT);
            System.exit(0);
        }

        Options mainOptions = this.getMainOptions();
        
        this.parseArguments(0, argArray.length, mainOptions);
    }

    private List<String> createLogger(List<String> argsWithLogger) {
        //TODO: provide an option to log to a file
        return argsWithLogger;
    }


    private String getArgValue(int argIndex) {
        if(argIndex == this.args.size() - 1) {
            return null;
        }

        if(this.args.get(argIndex + 1).substring(0, 1).equals("-")) {
            return null;
        }

        return this.args.get(argIndex + 1);
    }

    private void parseArguments(int start, int end, Options options) {
        int argIndex = start;

        while(argIndex < end) {
            String argName = this.args.get(argIndex);
//            String optionName = argName;
            String optionName = this.argToOption(argName);
            String argValue = this.getArgValue(argIndex);

            // The argument is boolean valued, and should be set to 1
            if(argValue == null) {

                try {
                    options.set(optionName, true);

                } catch(IllegalArgumentException error) {
                    this.printErrorFormat("Invalid argument: \"%s\"", argName);

                } catch(ClassCastException error) {
                    this.printErrorFormat("The argument \"%s\" requires a value", argName);
                }

                argIndex += 1;

            // The argument is differently valued, let the OptionList parse it
            } else {
//                System.out.print("\nThe option name is " + optionName +"\n");
                
//                
                String optionType = options.getType(optionName);
//                System.out.print("\nThe option type is " + optionType +"\n");
                Object optionValue;

                // If arraylist, fetch next argvalues
                if (optionType.equals(ArrayList.class.getName())){

                   // System.out.println(optionType);

                    ArrayList<String> files = new ArrayList<String>();
                    files.add(argValue);

                    while(argValue != null){
                        argValue = this.getArgValue(argIndex + 1);
                        if (argValue != null){
                            files.add(argValue);
                            argIndex++;
                        }
                    }
                    optionValue = files;

                } else if(optionType.equals(Integer.class.getName())){
                	optionValue = Integer.valueOf(argValue);
                }else if(optionType.equals(Float.class.getName())){
                	optionValue = Float.valueOf(argValue);
            	}else if(optionType.equals(File.class.getName())){
                	optionValue = new File(argValue);
                }else if(optionType.equals(Long.class.getName())){
                	optionValue = Long.valueOf(argValue);
                }else if(optionType.equals(Boolean.class.getName())){
                	optionValue = Boolean.valueOf(argValue);
                }else{
                    optionValue = argValue;
                }


                try {
                    if (optionType.equals(ArrayList.class.getName())){
                        options.set(optionName, (ArrayList<String>) optionValue);
                    } else if (optionType.equals(String.class.getName())) {
                        options.set(optionName, (String) optionValue);
                    } else if (optionType.equals(Integer.class.getName())) {
                    	
                    	//System.out.print("\nThis option is true " );
                    	options.set(optionName, optionValue);
                    } else if (optionType.equals(Long.class.getName())) {
                    	options.set(optionName, optionValue);
                    }else if (optionType.equals(Boolean.class.getName())) {
//                    	System.out.print("\nThis option is true " );

                    	options.set(optionName, optionValue);
                    }else{
                    	//System.out.print("\nThe optionName is " + optionName);
                        options.set(optionName, optionValue);
                    }

                } catch(NumberFormatException error) {
                    String type_ = options.getType(optionName);
                    this.printErrorFormat("The argument \"%s\" requires a value of type %s, got \"%s\"", argName, type_, argValue);

                } catch(IllegalArgumentException error) {
                    this.printErrorFormat("Invalid argument: \"%s\"", argName);
                }

                argIndex += 2;
            }
        }
    }

    private String argToOption(String argName) {
//        return argName.substring(2).replace("_", " ");
        return argName.substring(2).replace("--", " ");
    }


    private void printError(int argIndex) {
        String argValue = this.args.get(argIndex);
        this.printErrorFormat("Incorrect usage of the option \"%s\" at position %d%n%n", argValue, argIndex);
    }

    private void printErrorFormat(String format, Object... args) {
        this.printError(String.format(format, args));
    }
    private void printError(String message) {
        Stream stream = Stream.ERR;

        int numArgs = this.args.size();
        StringBuilder command = new StringBuilder(this.args.get(0));
        for(int i = 1; i < numArgs; i++) {
            command.append(" " + this.args.get(i));
        }

        this.logger.printf(stream, "Got arguments: %s\n\n", command.toString());
        this.logger.println(stream, message + "\n");
        this.printHelp(stream);

        System.exit(1);
    }

    //TODO
    private void printHelp(Stream stream) {
        Options mainOptions = this.getMainOptions();

        this.logger.print(stream, "usage: interfaces.CLI");
        this.printRequiredArguments(stream, mainOptions);
        this.logger.println(stream, " [general_options] [--placer placer_name1 [placer_options] [--placer placer_name2 [placer_options] [...]]]");
        this.logger.println(stream);

        this.logger.println(stream, "Attention: the order of arguments matters!");
        this.logger.println(stream, "The --placer option can be specified zero, one or multipler times.");
        this.logger.println(stream, "The chosen placers will be called in the provided order and with the specified options.");
        this.logger.println(stream, "Only the final placement is written to the --output_place_file.");
        this.logger.println(stream);

        this.logger.println(stream, "General options:");
        this.printOptionalArguments(stream, mainOptions);


    }

    private void printRequiredArguments(Stream stream, Options options) {
        for(String optionName : options.keySet()) {
            if(options.isRequired(optionName)) {
                this.logger.print(stream, " " + optionName.replace(" ", "_"));
            }
        }
    }

    private void printOptionalArguments(Stream stream, Options options) {
        int maxLength = options.getMaxNameLength();

        String format = String.format("  --%%-%ds   %%s%%s%n", maxLength);
        for(String optionName : options.keySet()) {
            if(!options.isRequired(optionName)) {
                String formattedName = optionName.replace(" ", "_");
                String optionDescription = options.getDescription(optionName);

                Object defaultValue = options.get(optionName);
                String defaultString = defaultValue == null ? "" : String.format(" (default: %s)", defaultValue.toString());

                this.logger.printf(stream, format, formattedName, optionDescription, defaultString);
            }
        }

        this.logger.println();
    }
}
