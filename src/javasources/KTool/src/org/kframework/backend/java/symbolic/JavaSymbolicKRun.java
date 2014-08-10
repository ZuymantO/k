// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import org.apache.commons.lang3.tuple.Pair;
import org.kframework.backend.java.kil.*;
import org.kframework.backend.java.kil.Rule;
import org.kframework.backend.pdmc.automaton.AutomatonInterface;
import org.kframework.backend.pdmc.k.JavaKRunPromelaEvaluator;
import org.kframework.backend.pdmc.k.JavaKRunPushdownSystem;
import org.kframework.backend.pdmc.k.PromelaTermAdaptor;
import org.kframework.backend.pdmc.pda.*;
import org.kframework.backend.pdmc.pda.buchi.*;
import org.kframework.backend.pdmc.pda.graph.TarjanSCC;
import org.kframework.backend.pdmc.pda.pautomaton.PAutomatonState;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Variable;
import org.kframework.backend.unparser.UnparserFilter;
import org.kframework.compile.transformers.DataStructureToLookupUpdate;
import org.kframework.compile.utils.CompilerStepDone;
import org.kframework.compile.utils.ConfigurationSubstitutionVisitor;
import org.kframework.compile.utils.MetaK;
import org.kframework.compile.utils.RuleCompilerSteps;
import org.kframework.compile.utils.Substitution;
import org.kframework.kil.Module;
import org.kframework.kil.Sort;
import org.kframework.kil.loader.Context;
import org.kframework.krun.ColorSetting;
import org.kframework.krun.KRunExecutionException;
import org.kframework.krun.SubstitutionFilter;
import org.kframework.krun.KRunOptions.OutputMode;
import org.kframework.krun.api.KRun;
import org.kframework.krun.api.KRunDebugger;
import org.kframework.krun.api.KRunProofResult;
import org.kframework.krun.api.KRunResult;
import org.kframework.krun.api.KRunState;
import org.kframework.krun.api.SearchResult;
import org.kframework.krun.api.SearchResults;
import org.kframework.krun.api.SearchType;
import org.kframework.krun.api.TestGenResult;
import org.kframework.krun.api.TestGenResults;
import org.kframework.krun.api.Transition;
import org.kframework.krun.api.UnsupportedBackendOptionException;
import org.kframework.krun.api.io.FileSystem;
import org.kframework.krun.ioserver.filesystem.portable.PortableFileSystem;

import java.util.*;

import edu.uci.ics.jung.graph.DirectedGraph;

import org.kframework.utils.general.IndexingStatistics;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 *
 * @author AndreiS
 */
public class JavaSymbolicKRun implements KRun {

    private final Definition definition;
    private final Context context;
    private final KILtoBackendJavaKILTransformer transformer;
    //Liyi Li: add a build-in SymbolicRewriter to fix the simulation rules
    private SymbolicRewriter simulationRewriter;

    @Inject
    JavaSymbolicKRun(Context context, Definition definition) {
        this.definition = definition;
        this.context = context;
        transformer = new KILtoBackendJavaKILTransformer(this.context);
    }

    public Definition getDefinition(){
        return this.definition;
    }

    @Override
    public KRunResult<KRunState> run(org.kframework.kil.Term cfg) throws KRunExecutionException {
        if (context.javaExecutionOptions.indexingStats){
            IndexingStatistics.totalKrunStopwatch.start();
            KRunResult<KRunState> result = internalRun(cfg, -1);
            IndexingStatistics.totalKrunStopwatch.stop();
            IndexingStatistics.print();
            return result;
        } else{
            return internalRun(cfg, -1);
        }
    }

    private KRunResult<KRunState> internalRun(org.kframework.kil.Term cfg, int bound) throws KRunExecutionException {
        ConstrainedTerm result = javaKILRun(cfg, bound);
        org.kframework.kil.Term kilTerm = (org.kframework.kil.Term) result.term().accept(
                new BackendJavaKILtoKILTransformer(context));
        KRunResult<KRunState> returnResult = new KRunResult<KRunState>(new KRunState(kilTerm, context), context);
        UnparserFilter unparser = new UnparserFilter(true, ColorSetting.OFF, OutputMode.PRETTY, context);
        unparser.visitNode(kilTerm);
        returnResult.setRawOutput(unparser.getResult());
        return returnResult;
    }

    private ConstrainedTerm javaKILRun(org.kframework.kil.Term cfg, int bound) {
        if (context.javaExecutionOptions.indexingStats){
            IndexingStatistics.preProcessStopWatch.start();
        }

        Term term = Term.of(cfg, definition);
        GlobalContext globalContext = new GlobalContext(definition, new PortableFileSystem());
        TermContext termContext = TermContext.of(globalContext);
        term = term.evaluate(termContext);

        if (context.javaExecutionOptions.patternMatching) {
            FastDestructiveRewriter rewriter = new FastDestructiveRewriter(definition, termContext);
            ConstrainedTerm rewriteResult = new ConstrainedTerm(rewriter.rewrite(term, bound), termContext);
            return rewriteResult;
        } else {
            SymbolicRewriter symbolicRewriter = new SymbolicRewriter(definition);
            SymbolicConstraint constraint = new SymbolicConstraint(termContext);
            ConstrainedTerm constrainedTerm = new ConstrainedTerm(term, constraint, termContext);
            if (context.javaExecutionOptions.indexingStats){
                IndexingStatistics.preProcessStopWatch.stop();
            }
            ConstrainedTerm rewriteResult;
            if (context.javaExecutionOptions.indexingStats) {
                IndexingStatistics.totalRewriteStopwatch.start();
                rewriteResult = symbolicRewriter.rewrite(constrainedTerm, bound);
                IndexingStatistics.totalRewriteStopwatch.stop();
            } else {
                rewriteResult = symbolicRewriter.rewrite(constrainedTerm, bound);
            }
            return rewriteResult;
        }
    }

    @Override
    public KRunProofResult<Set<org.kframework.kil.Term>> prove(Module module, org.kframework.kil.Term cfg) throws KRunExecutionException {
        GlobalContext globalContext = new GlobalContext(definition, new PortableFileSystem());
        TermContext termContext = TermContext.of(globalContext);
        Map<org.kframework.kil.Term, org.kframework.kil.Term> substitution = null;
        if (cfg != null) {
            cfg = run(cfg).getResult().getRawResult();
            ConfigurationSubstitutionVisitor configurationSubstitutionVisitor =
                    new ConfigurationSubstitutionVisitor(context);
            configurationSubstitutionVisitor.visitNode(cfg);
            substitution = configurationSubstitutionVisitor.getSubstitution();
//            System.out.println(substitution);
            Module mod = module;
            mod = (Module) new Substitution(substitution,context).visitNode(module);
//                System.out.println(mod.toString());
            module = mod;
        }
        try {
            module = new SpecificationCompilerSteps(context).compile(module, null);
        } catch (CompilerStepDone e) {
            assert false: "dead code";
        }
        List<ConstrainedTerm> proofResults = new ArrayList<ConstrainedTerm>();

        DataStructureToLookupUpdate mapTransformer = new DataStructureToLookupUpdate(context);

        List<Rule> rules = new ArrayList<Rule>();
        for (org.kframework.kil.ModuleItem moduleItem : module.getItems()) {
            if (!(moduleItem instanceof org.kframework.kil.Rule)) {
                continue;
            }

            Rule rule = transformer.transformRule(
                    (org.kframework.kil.Rule) mapTransformer.visitNode(moduleItem),
                    definition);
            Rule freshRule = rule.getFreshRule(termContext);
            rules.add(freshRule);
        }

        SymbolicRewriter symbolicRewriter = new SymbolicRewriter(definition);
        for (org.kframework.kil.ModuleItem moduleItem : module.getItems()) {
            if (!(moduleItem instanceof org.kframework.kil.Rule)) {
                continue;
            }

            org.kframework.kil.Rule kilRule = (org.kframework.kil.Rule) moduleItem;
            org.kframework.kil.Term kilLeftHandSide
                    = ((org.kframework.kil.Rewrite) kilRule.getBody()).getLeft();
            org.kframework.kil.Term kilRightHandSide =
                    ((org.kframework.kil.Rewrite) kilRule.getBody()).getRight();
            org.kframework.kil.Term kilRequires = kilRule.getRequires();
            org.kframework.kil.Term kilEnsures = kilRule.getEnsures();

            org.kframework.kil.Rule kilDummyRule = new org.kframework.kil.Rule(
                    kilRightHandSide,
                    MetaK.kWrap(org.kframework.kil.KSequence.EMPTY, "k"),
                    kilRequires,
                    kilEnsures,
                    context);
            Rule dummyRule = transformer.transformRule(
                    (org.kframework.kil.Rule) mapTransformer.visitNode(kilDummyRule),
                    definition);

            SymbolicConstraint initialConstraint = new SymbolicConstraint(termContext);
            initialConstraint.addAll(dummyRule.requires());
            ConstrainedTerm initialTerm = new ConstrainedTerm(
                    transformer.transformTerm(kilLeftHandSide, definition),
                    initialConstraint,
                    termContext);

            SymbolicConstraint targetConstraint = new SymbolicConstraint(termContext);
            targetConstraint.addAll(dummyRule.ensures());
            ConstrainedTerm targetTerm = new ConstrainedTerm(
                    dummyRule.leftHandSide().evaluate(termContext),
                    dummyRule.lookups().getSymbolicConstraint(termContext),
                    targetConstraint,
                    termContext);

            proofResults.addAll(symbolicRewriter.proveRule(initialTerm, targetTerm, rules));
        }

        System.err.println(proofResults.isEmpty());
        System.err.println(proofResults);

        return new KRunProofResult<Set<org.kframework.kil.Term>>(
                proofResults.isEmpty(), Collections.<org.kframework.kil.Term>emptySet(), context);
    }

    @Override
    public KRunResult<SearchResults> search(
            Integer bound,
            Integer depth,
            SearchType searchType,
            org.kframework.kil.Rule pattern,
            org.kframework.kil.Term cfg,
            RuleCompilerSteps compilationInfo) throws KRunExecutionException {

        if (context.javaExecutionOptions.indexingStats){
            IndexingStatistics.totalSearchStopwatch.start();
        }

        SymbolicRewriter symbolicRewriter = new SymbolicRewriter(definition);
        FileSystem fs = new PortableFileSystem();

        GlobalContext globalContext = new GlobalContext(definition, fs);
        List<Rule> claims = Collections.emptyList();
        if (bound == null) {
            bound = -1;
        }
        if (depth == null) {
            depth = -1;
        }

        // The pattern needs to be a rewrite in order for the transformer to be
        // able to handle it, so we need to give it a right-hand-side.
        org.kframework.kil.Cell c = new org.kframework.kil.Cell();
        c.setLabel("generatedTop");
        c.setContents(new org.kframework.kil.Bag());
        pattern.setBody(new org.kframework.kil.Rewrite(pattern.getBody(), c, context));
        Rule patternRule = transformer.transformRule(pattern, definition);

        List<SearchResult> searchResults = new ArrayList<SearchResult>();
        List<Map<Variable,Term>> hits;
        if (context.javaExecutionOptions.patternMatching) {
            Term initialTerm = Term.of(cfg, definition);
            Term targetTerm = null;
            GroundRewriter rewriter = new GroundRewriter(definition, TermContext.of(globalContext));
            hits = rewriter.search(initialTerm, targetTerm, claims,
                    patternRule, bound, depth, searchType);
        } else {
            ConstrainedTerm initialTerm = new ConstrainedTerm(Term.of(cfg, definition), TermContext.of(globalContext));
            ConstrainedTerm targetTerm = null;
            SymbolicRewriter rewriter = new SymbolicRewriter(definition);
            hits = rewriter.search(initialTerm, targetTerm, claims,
                    patternRule, bound, depth, searchType);
        }

        for (Map<Variable,Term> map : hits) {
            // Construct substitution map from the search results
            Map<String, org.kframework.kil.Term> substitutionMap =
                    new HashMap<String, org.kframework.kil.Term>();
            for (Variable var : map.keySet()) {
                org.kframework.kil.Term kilTerm =
                        (org.kframework.kil.Term) map.get(var).accept(
                                new BackendJavaKILtoKILTransformer(context));
                substitutionMap.put(var.toString(), kilTerm);
            }

            // Apply the substitution to the pattern
            org.kframework.kil.Term rawResult =
                    (org.kframework.kil.Term) new SubstitutionFilter(substitutionMap, context)
                        .visitNode(pattern.getBody());

            searchResults.add(new SearchResult(
                    new KRunState(rawResult, context),
                    substitutionMap,
                    compilationInfo,
                    context));
        }

        // TODO(ericmikida): Make the isDefaultPattern option set in some reasonable way
        KRunResult<SearchResults> searchResultsKRunResult = new KRunResult<>(new SearchResults(
                searchResults,
                null,
                false,
                context), context);

        if (context.javaExecutionOptions.indexingStats){
            IndexingStatistics.totalSearchStopwatch.stop();
            IndexingStatistics.print();
        }
        return searchResultsKRunResult;
    }

    @Override
    public KRunResult<TestGenResults> generate(
            Integer bound,
            Integer depth,
            SearchType searchType,
            org.kframework.kil.Rule pattern,
            org.kframework.kil.Term cfg,
            RuleCompilerSteps compilationInfo) throws KRunExecutionException {
        throw new UnsupportedBackendOptionException("--test-gen");
    }

    @Override
    public KRunProofResult<DirectedGraph<KRunState, Transition>> modelCheck(
            org.kframework.kil.Term formula,
            org.kframework.kil.Term cfg) throws KRunExecutionException {
        if (context.javaExecutionOptions.pdmc.isEmpty())
            throw new UnsupportedBackendOptionException("--ltlmc");
        assert formula instanceof PromelaTermAdaptor;
        return pdmc(cfg, ((PromelaTermAdaptor) formula).getAutomaton());
    }


    public KRunProofResult<DirectedGraph<KRunState, Transition>> pdmc(
            org.kframework.kil.Term cfg, PromelaBuchi automaton) throws KRunExecutionException {
        Term term = Term.of(cfg, definition);
        JavaKRunPushdownSystem pds = new JavaKRunPushdownSystem(this, term);
        JavaKRunPromelaEvaluator evaluator = new JavaKRunPromelaEvaluator(pds);
        BuchiPushdownSystem<Term, Term> buchiPushdownSystem = new BuchiPushdownSystem<>(pds, automaton, evaluator);
        System.err.println("\n----Buchi Pushdown System---");
//        System.err.print(buchiPushdownSystem.toString());

        BuchiPushdownSystemTools<Term, Term> bpsTool = new BuchiPushdownSystemTools<>(buchiPushdownSystem);

        AutomatonInterface<PAutomatonState<Pair<Term, BuchiState>, Term>, Term> post = bpsTool.getPostStar();
        System.err.println("\n\n\n----Post Automaton----");
        System.err.println(post.toString());

        TarjanSCC repeatedHeads = bpsTool.getRepeatedHeadsGraph();
        System.err.println("\n\n\n----Repeated Heads----");
        System.err.println(repeatedHeads.toString());

        System.err.println("\n\n\n----Strongly Connected Components----");
        System.err.println(repeatedHeads.getSCCSString());

        TarjanSCC<ConfigurationHead<Pair<Term, BuchiState>, Term>, TrackingLabel<Pair<Term, BuchiState>, Term>> counterExampleGraph
                = bpsTool.getCounterExampleGraph();
        if (counterExampleGraph == BuchiPushdownSystemTools.NONE) {
            System.out.println("No counterexample found. Property holds.");
        } else {
            System.out.println("Counterexample found for the given property");
            ConfigurationHead<Pair<Term, BuchiState>, Term> head = counterExampleGraph.getVertices().iterator().next();
            System.out.println("\n\n\n--- Prefix path ---");
            Deque<org.kframework.backend.pdmc.pda.Rule<Pair<Term, BuchiState>, Term>> reachableConfigurationPath = bpsTool.getReachableConfiguration(head);
            for(org.kframework.backend.pdmc.pda.Rule<Pair<Term, BuchiState>, Term> rule : reachableConfigurationPath) {
                System.out.print(rule.getLabel().toString() + ";");
            }
            System.out.print("[");
            Deque<org.kframework.backend.pdmc.pda.Rule<Pair<Term, BuchiState>, Term>> repeatingCycle = bpsTool.getRepeatingCycle(head);
            for (org.kframework.backend.pdmc.pda.Rule<Pair<Term, BuchiState>, Term> rule : repeatingCycle) {
                System.out.print(rule.getLabel().toString() + ";");
            }
            System.out.println("]");
        }
        if (false) {
            TrackingLabelFactory<Term, Term> factory = new TrackingLabelFactory<>();
            PostStar<Term, Term> postStar = new PostStar<>(pds, factory);


            for (ConfigurationHead<Term, Term> head : postStar.getReachableHeads()) {
                Deque<org.kframework.backend.pdmc.pda.Rule<Term, Term>> path = postStar.getReachableConfiguration(head);
                System.out.println(head.toString() + ":");
                for (org.kframework.backend.pdmc.pda.Rule<Term, Term> rule : path) {
                    ConfigurationHead<Term, Term> head1 = rule.getHead();

                    Term kStartConfig = pds.getKConfig(head1.getState(), head1.getLetter());
                    Term kEndConfig = pds.getKConfig(rule.endConfiguration());
                    System.out.println(kStartConfig + " => " + kEndConfig);
                }
//            System.out.println("\t" + path);
            }
        }
        DirectedGraph<KRunState, Transition> kRunStateTransitionDirectedSparseGraph = new DirectedSparseGraph<>();
        return new KRunProofResult<>(false, kRunStateTransitionDirectedSparseGraph, context);

    }


    @Override
    public KRunResult<KRunState> step(org.kframework.kil.Term cfg, int steps)
            throws KRunExecutionException {
        return internalRun(cfg, steps);
    }

    /*
     * author: Liyi Li
     * to get the symbolic rewriter
     */
    public SymbolicRewriter getSimulationRewriter(){

        return this.simulationRewriter;
    }

    public void initialSimulationRewriter(){

        this.simulationRewriter = new SymbolicRewriter(definition);
    }

    /* author: Liyi Li
     * a function return all the next step of a given simulation term
     */
    public ConstrainedTerm simulationSteps(ConstrainedTerm cfg)
            throws KRunExecutionException {

        ConstrainedTerm result = this.simulationRewriter.computeSimulationStep(cfg);

        return result;
    }

    /* author: Liyi Li
     * a function return all the next steps of a given term
     */
    public ArrayList<ConstrainedTerm> steps(ConstrainedTerm cfg)
            throws KRunExecutionException {

        ArrayList<ConstrainedTerm> results = this.simulationRewriter.rewriteAll(cfg);



        return results;
    }


    @Override
    public KRunDebugger debug(org.kframework.kil.Term cfg) {
        throw new UnsupportedBackendOptionException("--debug");
    }

    @Override
    public KRunDebugger debug(DirectedGraph<KRunState, Transition> graph) {
        throw new UnsupportedBackendOptionException("--debug");
    }

    @Override
    public void setBackendOption(String key, Object value) {
    }

}
