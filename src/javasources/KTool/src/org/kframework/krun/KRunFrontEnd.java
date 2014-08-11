// Copyright (c) 2010-2014 K Team. All Rights Reserved.
package org.kframework.krun;

import static org.apache.commons.io.FileUtils.writeStringToFile;

import java.io.*;
import java.util.LinkedList;
import java.util.List;

import jline.ArgumentCompletor;
import jline.Completor;
import jline.ConsoleReader;
import jline.FileNameCompletor;
import jline.MultiCompletor;
import jline.SimpleCompletor;

import org.fusesource.jansi.AnsiString;
import org.kframework.backend.java.ksimulation.Waitor;
import org.kframework.backend.java.symbolic.JavaExecutionOptions;
import org.kframework.backend.pdmc.k.PromelaTermAdapter;
import org.kframework.backend.pdmc.pda.buchi.PromelaBuchi;
import org.kframework.backend.pdmc.pda.buchi.parser.ParseException;
import org.kframework.backend.pdmc.pda.buchi.parser.PromelaBuchiParser;
import org.kframework.compile.utils.CompilerStepDone;
import org.kframework.compile.utils.RuleCompilerSteps;
import org.kframework.kil.ASTNode;
import org.kframework.kil.Definition;
import org.kframework.kil.Module;
import org.kframework.kil.Rule;
import org.kframework.kil.Sentence;
import org.kframework.kil.StringBuiltin;
import org.kframework.kil.Term;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.exceptions.ParseFailedException;
import org.kframework.krun.KRunOptions.OutputMode;
import org.kframework.krun.api.KRun;
import org.kframework.krun.api.KRunDebugger;
import org.kframework.krun.api.KRunResult;
import org.kframework.krun.api.KRunState;
import org.kframework.krun.api.SearchResults;
import org.kframework.krun.api.SearchType;
import org.kframework.krun.api.Transition;
import org.kframework.krun.api.UnsupportedBackendOptionException;
import org.kframework.krun.gui.Controller.RunKRunCommand;
import org.kframework.krun.gui.UIDesign.MainWindow;
import org.kframework.main.FrontEnd;
import org.kframework.parser.DefinitionLoader;
import org.kframework.utils.BinaryLoader;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.inject.JCommanderModule;
import org.kframework.utils.inject.JCommanderModule.ExperimentalUsage;
import org.kframework.utils.inject.JCommanderModule.Usage;
import org.kframework.utils.inject.CommonModule;
import org.kframework.utils.inject.Main;
import org.kframework.utils.options.SMTOptions;
import org.kframework.utils.options.SortedParameterDescriptions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Provider;
import edu.uci.ics.jung.graph.DirectedGraph;

public class KRunFrontEnd extends FrontEnd {

    /**
     * Execute krun in normal mode (i.e. not in debug mode).
     * @param executionContext the object containing the initial configuration
     * and definition context to use to execute.
     * @return true if the application completed normally; false otherwise
     */
    public boolean normalExecution(KRun krun, Context context, Term initialConfiguration) {

        try {
            KRunOptions options = context.krunOptions;
            KRunResult<?> result = null;
            //Set<String> varNames = null;
            try {
                Rule patternRule = null;
                RuleCompilerSteps steps = null;
                if (options.pattern(context) != null) {
                    steps = new RuleCompilerSteps(context);
                    ASTNode pattern = options.pattern(context);
                    //varNames = vars.getVars().keySet();

                    try {
                        pattern = steps.compile(new Rule((Sentence) pattern), null);
                    } catch (CompilerStepDone e) {
                        pattern = (ASTNode) e.getResult();
                    }
                    patternRule = new Rule((Sentence) pattern);
                    sw.printIntermediate("Parsing search pattern");
                }
                if (options.search()) {

                    if(options.experimental.javaExecution.generateTests){
                        result = krun.generate(
                                options.bound,
                                options.depth,
                                options.searchType(),
                                patternRule,
                                initialConfiguration, steps);
                    } else{
                        result = krun.search(
                                options.bound,
                                options.depth,
                                options.searchType(),
                                patternRule,
                                initialConfiguration, steps);
                    }

                    sw.printIntermediate("Search total");
                } else if (options.experimental.ltlmc() != null) {
                    Term formula = new RunProcess().runParserOrDie("kast -e",
                            options.experimental.ltlmc(), false, "LtlFormula", context);

                    result = krun.modelCheck(
                                    formula,
                                    initialConfiguration);

                    sw.printIntermediate("Model checking total");
                } else  if (!context.javaExecutionOptions.pdmc.isEmpty()) {
                    try {
                        PromelaBuchi automaton = PromelaBuchiParser.parse(new FileInputStream(context.javaExecutionOptions.pdmc));
                        result = krun.modelCheck(new PromelaTermAdapter(automaton), initialConfiguration);
                    } catch (ParseException e) {
                        org.kframework.utils.Error.report("Cannot parse SPIN never claim file: " + context.javaExecutionOptions.pdmc + "\n"
                                + e.getMessage());
                    }
                } else if (options.experimental.prove() != null) {
                    File proofFile = options.experimental.prove();
                    String content = FileUtil.getFileContent(
                            proofFile.getAbsoluteFile().toString());
                    Definition parsed = DefinitionLoader.parseString(content,
                            proofFile.getAbsolutePath(), context);
                    Module mod = parsed.getSingletonModule();
                    result = krun.prove(mod, initialConfiguration);
                    sw.printIntermediate("Proof total");
                } else if (options.depth != null) {
                    result = krun.step(initialConfiguration, options.depth);
                    sw.printIntermediate("Bounded execution total");
                } else {
                    result = krun.run(initialConfiguration);
                    sw.printIntermediate("Normal execution total");
                }
                if (patternRule != null && !options.search()) {
                    Object krs = result.getResult();
                    assert krs instanceof KRunState;
                    Term res = ((KRunState) krs).getRawResult();
                    krun.setBackendOption("io", false);
                    result = krun.search(1, 1, SearchType.FINAL, patternRule, res, steps);
                }

            } catch (KRunExecutionException e) {
                if (context.globalOptions.debug) {
                    e.printStackTrace();
                }
                new RunProcess().printError(e.getMessage());
                return false;
            } catch (ParseFailedException e) {
                e.report();
            } catch (UnsupportedBackendOptionException e) {
                kem.registerCriticalError("Backend \""
                                + context.kompileOptions.backend.name().toLowerCase()
                                + "\" does not support option " + e.getMessage(), e);
            }

            if (options.output.isPrettyPrinting()) {

                String output = result.toString();

                writeOutput(options, output);
                // print search graph
                if (options.search() && options.graph) {
                    System.out.println("\nThe search graph is:\n");
                    @SuppressWarnings("unchecked")
                    KRunResult<SearchResults> searchResult = (KRunResult<SearchResults>) result;
                    System.out.println(searchResult.getResult().getGraph());
                    // offer the user the possibility to turn execution into
                    // debug mode
                    while (true) {
                        System.out.print("\nDo you want to enter in debug mode? (y/n):");
                        BufferedReader stdin = new BufferedReader(
                                new InputStreamReader(System.in));
                        System.out.flush();
                        String input = stdin.readLine();
                        if (input == null || input.equals("n")) {
                            System.out.println();
                            break;
                        } else if (input.equals("y")) {
                            debugExecution(krun, context, initialConfiguration, searchResult);
                            break;
                        } else {
                            System.out
                                    .println("You should specify one of the possible answers:y or n");
                        }
                    }
                }
            } else if (options.output == OutputMode.RAW) {
                String output = result.getRawOutput();
                writeOutput(options, output);
            } else if (options.output == OutputMode.NONE) {
            } else if (options.output == OutputMode.BINARY) {
                Object krs = result.getResult();
                if (krs instanceof KRunState) {
                    Term res = ((KRunState) krs).getRawResult();

                    if (options.experimental.outputFile == null) {
                        loader.saveOrDie(System.out, res);

                    } else {
                        loader.saveOrDie(options.experimental.outputFile.getAbsolutePath(), res);
                    }
                } else {
                    kem.registerCriticalError("Binary output mode is not supported by search " +
                            "and model checking");
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        sw.printTotal("Total");
        return true;
    }
    private static void writeOutput(KRunOptions options, String output)
            throws IOException {
        if (options.experimental.outputFile == null) {
            System.out.println(output);
        } else {
            output = new AnsiString(output).getPlain().toString();
            writeStringToFile(options.experimental.outputFile, output);
        }
    }

    private static Object command(JCommander jc) {
        return jc.getCommands().get(jc.getParsedCommand()).getObjects().get(0);
    }

    /**
     *  execute krun in debug mode (i.e. step by step execution)
     * state is not null if we enter in debug execution from normal
     * execution (we use the search command with --graph option)
     * @param initialConfiguration The initial configuration to execute with in the debugger
     * @param state An optional set of search results to initialize with the initial state of the debugger.
     * @param context The definition context loaded from the compiled definition.
     * @return true if the application completed normally; false otherwise
     */
    public boolean debugExecution(KRun krun, Context context, Term initialConfiguration, KRunResult<SearchResults> state) {
        ConsoleReader reader;
        try {
            reader = new ConsoleReader();
        } catch (IOException e) {
            kem.registerInternalError("IO error detected interacting with console", e);
            return false;
        }
        try {
            // adding autocompletion and history feature to the stepper internal
            // commandline by using the JLine library
            reader.setBellEnabled(false);

            List<Completor> argCompletor = new LinkedList<Completor>();
            argCompletor.add(new SimpleCompletor(new String[] { "help",
                    "exit", "resume", "step", "search", "select",
                    "show-graph", "show-state", "show-transition", "save", "load", "read" }));
            argCompletor.add(new FileNameCompletor());
            List<Completor> completors = new LinkedList<Completor>();
            completors.add(new ArgumentCompletor(argCompletor));
            reader.addCompletor(new MultiCompletor(completors));

            krun.setBackendOption("io", false);
            KRunDebugger debugger;
            try {
                if (state == null) {
                    debugger = krun.debug(initialConfiguration);
                    System.out
                            .println("After running one step of execution the result is:");
                    System.out.println(debugger.printState(debugger.getCurrentState()));
                } else {
                    debugger = krun.debug(state.getResult().getGraph());
                }
            } catch (UnsupportedBackendOptionException e) {
                kem.registerCriticalError("Backend \""
                        + context.kompileOptions.backend.name().toLowerCase()
                        + "\" does not support option " + e.getMessage(), e);
                return false; //unreachable
            }
            while (true) {
                System.out.println();
                String input;
                try {
                    input = reader.readLine("Command > ");
                } catch (IOException e) {
                    kem.registerInternalError("IO error detected interacting with console", e);
                    return false;
                }
                if (input == null) {
                    // probably pressed ^D
                    System.out.println();
                    return true;
                }
                if (input.equals("")) {
                    continue;
                }

                KRunDebuggerOptions options = new KRunDebuggerOptions();
                JCommander jc = new JCommander(options);
                jc.addCommand(options.help);
                jc.addCommand(options.step);
                jc.addCommand(options.search);
                jc.addCommand(options.select);
                jc.addCommand(options.showGraph);
                jc.addCommand(options.showState);
                jc.addCommand(options.showTransition);
                jc.addCommand(options.resume);
                jc.addCommand(options.exit);
                jc.addCommand(options.save);
                jc.addCommand(options.load);
                jc.addCommand(options.read);

                try {
                    jc.parse(input.split("\\s+"));

                    if (jc.getParsedCommand().equals("help")) {
                        if (options.help.command == null || options.help.command.size() == 0) {
                            jc.usage();
                        } else {
                            for (String command : options.help.command) {
                                if (jc.getCommands().containsKey(command)) {
                                    jc.usage(command);
                                }
                            }
                        }
                    } else if (command(jc) instanceof KRunDebuggerOptions.CommandExit) {
                        return true;
                    } else if (command(jc) instanceof KRunDebuggerOptions.CommandResume) {
                        debugger.resume();
                        System.out.println(debugger.printState(debugger.getCurrentState()));
                    } else if (command(jc) instanceof KRunDebuggerOptions.CommandStep) {
                        debugger.step(options.step.numSteps);
                        System.out.println(debugger.printState(debugger.getCurrentState()));
                    } else if (command(jc) instanceof KRunDebuggerOptions.CommandSearch) {
                        SearchResults states = debugger.stepAll(options.search.numSteps);
                        System.out.println(states);
                    } else if (command(jc) instanceof KRunDebuggerOptions.CommandSelect) {
                        debugger.setCurrentState(options.select.stateId);
                        System.out.println(debugger.printState(debugger.getCurrentState()));
                    } else if (command(jc) instanceof KRunDebuggerOptions.CommandShowGraph) {
                        System.out.println("\nThe search graph is:\n");
                        System.out.println(debugger.getGraph());
                    } else if (command(jc) instanceof KRunDebuggerOptions.CommandShowState) {
                        System.out.println(debugger.printState(options.showState.stateId));
                    } else if (command(jc) instanceof KRunDebuggerOptions.CommandShowTransition) {
                        int state1 = options.showTransition.state1();
                        int state2 = options.showTransition.state2();
                        System.out.println(debugger.printEdge(state1, state2));
                    } else if (command(jc) instanceof KRunDebuggerOptions.CommandSave) {
                        loader.saveOrDie(options.save.file.getAbsolutePath(), debugger.getGraph());
                        System.out.println("File successfully saved.");
                    } else if (command(jc) instanceof KRunDebuggerOptions.CommandLoad) {
                        @SuppressWarnings("unchecked")
                        DirectedGraph<KRunState, Transition> savedGraph =
                                loader.loadOrDie(DirectedGraph.class, options.load.file.getAbsolutePath());
                        debugger = krun.debug(savedGraph);
                        debugger.setCurrentState(1);
                        System.out.println("File successfully loaded.");
                    } else if (command(jc) instanceof KRunDebuggerOptions.CommandRead) {
                        debugger.readFromStdin(StringBuiltin.valueOf(options.read.string).stringValue());
                        System.out.println(debugger.printState(debugger.getCurrentState()));
                    } else {
                        assert false : "Unexpected krun debugger command " + jc.getParsedCommand();
                    }
                } catch (ParameterException e) {
                    System.err.println(e.getMessage());
                    jc.usage();
                } catch (IllegalArgumentException | IllegalStateException e) {
                    System.err.println(e.getMessage());
                }
            }
        } catch (KRunExecutionException e) {
            if (context.globalOptions.debug) {
                e.printStackTrace();
            }
            new RunProcess().printError(e.getMessage());
            return false;
        }
    }

    private boolean guiDebugExecution(KRun krun, Context context, Term initialConfiguration) {
        try {
            krun.setBackendOption("io", false);
            RunKRunCommand cmd = new RunKRunCommand(initialConfiguration, krun, context);
            MainWindow window = new MainWindow(cmd);
            synchronized(window.lock) {
                while (true) {
                    try {
                        window.lock.wait();
                        return true;
                    } catch (InterruptedException e) {
                        //keep waiting
                    }
                }
            }
        } catch (KRunExecutionException e) {
            kem.registerCriticalError(e.getMessage(), e);
            return false;
        }
    }

    public static com.google.inject.Module[] getModules(String[] args) {
        try {
            KRunOptions options = new KRunOptions();

            JCommander jc = new JCommander(options, args);
            jc.setProgramName("krun");
            jc.setParameterDescriptionComparator(new SortedParameterDescriptions(KRunOptions.Experimental.class, SMTOptions.class, JavaExecutionOptions.class));

            if (options.experimental.debuggerGui()) {
                System.setProperty("java.awt.headless", "false");
            }

            if (options.experimental.simulation != null) {
                return new com.google.inject.Module[] {
                        new KRunModule(options),
                        new CommonModule(),
                        new JCommanderModule(jc),
                        new KRunModule.SimulationModule(),
                        new KRunModule.MainExecutionContextModule(options) };
            } else {
                return new com.google.inject.Module[] {
                        new KRunModule(options),
                        new CommonModule(),
                        new JCommanderModule(jc),
                        new KRunModule.NoSimulationModule(options) };
            }
        } catch (ParameterException ex) {
            printBootError(ex.getMessage());
            return null;
        }
    }

    private final KRunOptions options;
    private final Provider<KRun> krunProvider;
    private final Provider<Context> contextProvider;
    private final Provider<Term> initialConfigurationProvider;
    private final Provider<Optional<Waitor>> waitorProvider;
    private final Stopwatch sw;
    private final KExceptionManager kem;
    private final BinaryLoader loader;

    @Inject
    KRunFrontEnd(
            KRunOptions options,
            @Usage String usage,
            @ExperimentalUsage String experimentalUsage,
            @Main Provider<KRun> krunProvider,
            @Main Provider<Context> contextProvider,
            @Main Provider<Term> initialConfigurationProvider,
            Provider<Optional<Waitor>> waitorProvider,
            Stopwatch sw,
            KExceptionManager kem,
            BinaryLoader loader) {
        super(kem, options.global, usage, experimentalUsage);
        this.options = options;
        this.krunProvider = krunProvider;
        this.contextProvider = contextProvider;
        this.initialConfigurationProvider = initialConfigurationProvider;
        this.waitorProvider = waitorProvider;
        this.sw = sw;
        this.kem = kem;
        this.loader = loader;
    }

    /**
     * @param cmds represents the arguments/options given to krun command..
     * @return true if the application completed normally; false otherwise
     */
    public boolean run() {
        if (options.experimental.simulation != null) {
            Waitor runSimulation = waitorProvider.get().get();
            runSimulation.start();
            try {
                runSimulation.join();
            } catch (InterruptedException e) {
                return false;
            }
            return true;
        }

        KRun krun = krunProvider.get();
        Term initialConfiguration = initialConfigurationProvider.get();
        Context context = contextProvider.get();

        if (!options.experimental.debugger() && !options.experimental.debuggerGui()) {
            normalExecution(krun, context, initialConfiguration);
        } else {
            if (options.experimental.debuggerGui())
                return guiDebugExecution(krun, context, initialConfiguration);
            else
                debugExecution(krun, context, initialConfiguration, null);
        }
        return true;
    }
}
