// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.backend.java.builtins;

import org.kframework.backend.java.kil.Definition;
import org.kframework.backend.java.kil.KItem;
import org.kframework.backend.java.kil.KLabelConstant;
import org.kframework.backend.java.kil.KLabelInjection;
import org.kframework.backend.java.kil.KList;
import org.kframework.backend.java.kil.KSequence;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.symbolic.KILtoBackendJavaKILTransformer;
import org.kframework.kil.GeneratedSource;
import org.kframework.kil.Sort;
import org.kframework.kil.Source;
import org.kframework.kil.loader.Constants;
import org.kframework.kil.loader.Context;
import org.kframework.utils.errorsystem.ParseFailedException;
import org.kframework.krun.KRunOptions.ConfigurationCreationOptions;
import org.kframework.krun.RunProcess;
import org.kframework.krun.RunProcess.ProcessOutput;
import org.kframework.krun.api.io.FileSystem;
import com.google.inject.Inject;
import org.kframework.parser.ProgramLoader;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.util.Map;
import java.util.HashMap;


/**
 * Table of {@code public static} methods for builtin IO operations.
 */
public class BuiltinIOOperationsImpl implements BuiltinIOOperations {

    private final Definition def;
    private final FileSystem fs;
    private final Context context;
    private final ConfigurationCreationOptions ccOptions;
    private final KILtoBackendJavaKILTransformer kilTransformer;
    private final ProgramLoader programLoader;
    private final RunProcess rp;

    @Inject
    public BuiltinIOOperationsImpl(
            Definition def,
            FileSystem fs,
            Context context,
            ConfigurationCreationOptions ccOptions,
            KILtoBackendJavaKILTransformer kilTransformer,
            ProgramLoader programLoader,
            RunProcess rp) {
        this.def = def;
        this.fs = fs;
        this.context = context;
        this.ccOptions = ccOptions;
        this.kilTransformer = kilTransformer;
        this.programLoader = programLoader;
        this.rp = rp;
    }

    @Override
    public Term open(StringToken term1, StringToken term2, TermContext termContext) {
        try {
            return IntToken.of(fs.open(term1.stringValue(), term2.stringValue()));
        } catch (IOException e) {
            return processIOException(e.getMessage(), termContext);
        }
    }

    @Override
    public Term tell(IntToken term, TermContext termContext) {
        try {
            return IntToken.of(fs.get(term.longValue()).tell());
        } catch (IOException e) {
            return processIOException(e.getMessage(), termContext);
        }
    }

    @Override
    public Term getc(IntToken term, TermContext termContext) {
        try {
            return IntToken.of(fs.get(term.longValue()).getc() & 0xff);
        } catch (IOException e) {
            return processIOException(e.getMessage(), termContext);
        }
    }

    @Override
    public Term read(IntToken term1, IntToken term2, TermContext termContext) {
        try {
            return StringToken.of(fs.get(term1.longValue()).read(term2.intValue()));
        } catch (IOException e) {
            return processIOException(e.getMessage(), termContext);
        }
    }

    @Override
    public Term close(IntToken term, TermContext termContext) {
        try {
            fs.close(term.longValue());
            return KLabelInjection.injectionOf(KSequence.EMPTY, termContext);
        } catch (IOException e) {
            return KLabelInjection.injectionOf(
                    processIOException(e.getMessage(), termContext),
                    termContext);
        }
    }

    @Override
    public Term seek(IntToken term1, IntToken term2, TermContext termContext) {
        try {
            fs.get(term1.longValue()).seek(term2.longValue());
            return KLabelInjection.injectionOf(KSequence.EMPTY, termContext);
        } catch (IOException e) {
            return KLabelInjection.injectionOf(
                    processIOException(e.getMessage(), termContext),
                    termContext);
        }
    }

    @Override
    public Term putc(IntToken term1, IntToken term2, TermContext termContext) {
        try {
            fs.get(term1.longValue()).putc(term2.unsignedByteValue());
            return KLabelInjection.injectionOf(KSequence.EMPTY, termContext);
        } catch (IOException e) {
            return KLabelInjection.injectionOf(
                    processIOException(e.getMessage(), termContext),
                    termContext);
        }
    }

    @Override
    public Term write(IntToken term1, StringToken term2, TermContext termContext) {
        try {
            fs.get(term1.longValue()).write(term2.byteArrayValue());
            return KLabelInjection.injectionOf(KSequence.EMPTY, termContext);
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(e);
        } catch (IOException e) {
            return KLabelInjection.injectionOf(
                    processIOException(e.getMessage(), termContext),
                    termContext);
        }
    }

    @Override
    public Term parse(StringToken term1, StringToken term2, TermContext termContext) {
        try {
            org.kframework.kil.Term kast = rp.runParser(
                    ccOptions.parser(context),
                    term1.stringValue(), true, Sort.of(term2.stringValue()), context);
            Term term = kilTransformer.transformAndEval(kast);
            return term;
        } catch (ParseFailedException e) {
            String line = "" + e.getKException().getLocation().lineStart;
            String col  = "" + e.getKException().getLocation().columnStart;
            return processIOException(Constants.NOPARSE, StringToken.of(e.getMessage() + " Line: " + line + " Column: " + col), termContext);
        }
    }

    @Override
    public Term parseInModule(StringToken input, StringToken startSymbol, StringToken moduleName, TermContext termContext) {
        try {
            org.kframework.kil.Term kast = programLoader.parseInModule(
                    input.stringValue(),
                    new GeneratedSource(this.getClass()),
                    Sort.of(startSymbol.stringValue()),
                    moduleName.stringValue(), context);
            Term term = kilTransformer.transformAndEval(kast);
            term = term.evaluate(termContext);
            return term;
        } catch (ParseFailedException e) {
            String line = "" + e.getKException().getLocation().lineStart;
            String col  = "" + e.getKException().getLocation().columnStart;
            return processIOException(Constants.NOPARSE, StringToken.of(e.getMessage() + " Line: " + line + " Column: " + col), termContext);
        }
    }

    @Override
    public Term system(StringToken term, TermContext termContext) {
        Map<String, String> environment = new HashMap<>();
        String[] args = term.stringValue().split("\001", -1);
        //for (String c : args) { System.out.println(c); }
        ProcessOutput output = rp.execute(environment, args);

        KLabelConstant klabel = KLabelConstant.of("'#systemResult(_,_,_)", context);
        /*
        String klabelString = "'#systemResult(_,_,_)";
        KLabelConstant klabel = KLabelConstant.of(klabelString, context);
        assert def.kLabels().contains(klabel) : "No KLabel in definition for " + klabelString;
        */
        String stdout = output.stdout != null ? output.stdout : "";
        String stderr = output.stderr != null ? output.stderr : "";
        return KItem.of(klabel, KList.concatenate(IntToken.of(output.exitCode),
            StringToken.of(stdout.trim()), StringToken.of(stderr.trim())), termContext);
    }

    private KItem processIOException(String errno, Term klist, TermContext termContext) {
        String klabelString = "'#" + errno;
        KLabelConstant klabel = KLabelConstant.of(klabelString, context);
        assert def.kLabels().contains(klabel) : "No KLabel in definition for errno '" + errno + "'";
        return KItem.of(klabel, klist, termContext);
    }

    private KItem processIOException(String errno, TermContext termContext) {
        return processIOException(errno, KList.EMPTY, termContext);
    }
}
