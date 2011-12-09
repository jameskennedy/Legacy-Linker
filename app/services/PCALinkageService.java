package services;

import japa.parser.JavaParser;
import japa.parser.ParseException;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.body.BodyDeclaration;
import japa.parser.ast.body.ClassOrInterfaceDeclaration;
import japa.parser.ast.body.EnumDeclaration;
import japa.parser.ast.body.JavadocComment;
import japa.parser.ast.body.MethodDeclaration;
import japa.parser.ast.body.TypeDeclaration;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import models.GITRepository;
import models.PCAProgram;
import models.PCAProgramClassLink;
import models.PCAProgramMethodLink;
import models.RepoCommit;
import models.RepoFile;
import play.Logger;
import play.db.jpa.NoTransaction;

public class PCALinkageService {

    private static Pattern LEGACY_PROGRAM = Pattern.compile("@legacy[\\s\\t]*([a-zA-Z][a-zA-Z0-9]{2,7})");
    private static Pattern COMMIT_PROGRAM = Pattern.compile("([a-zA-Z][a-zA-Z0-9]{2,7})");

    @NoTransaction
    public static void updateFileLinkage(final GITRepository repo, final Set<RepoFile> files) {
        int filesToProcess = files.size();
        int filesProcessed = 0;
        for (RepoFile repoFile : files) {
            filesProcessed++;

            // Drop all existing links
            PCAProgramClassLink.delete("file = ?", repoFile);

            String path = repoFile.getAbsolutePath();
            File file = new File(path);

            if (!file.exists()) {
                Logger.debug("[%d/%d] Unlinked deleted repository file %s", filesProcessed, filesToProcess,
                                file.getAbsoluteFile());
                continue;
            }

            CompilationUnit cu = loadJavaFile(repoFile);

            List<TypeDeclaration> types = cu.getTypes();
            if (null == types) {
                Logger.warn("[%d/%d] File %s has no type declaration.", filesProcessed, filesToProcess, path);
                continue;
            }

            String author = null;
            for (TypeDeclaration type : types) {
                ClassOrInterfaceDeclaration clazz = null;
                int classLineCount = 0;

                Map<String, PCAProgramClassLink> classProgramTable = new HashMap<String, PCAProgramClassLink>();
                Map<String, PCAProgramClassLink> indirectClassProgramTable = new HashMap<String, PCAProgramClassLink>();

                if (type instanceof ClassOrInterfaceDeclaration || type instanceof EnumDeclaration) {
                    clazz = (ClassOrInterfaceDeclaration) type;
                    classLineCount = clazz.getEndLine() - clazz.getBeginLine() + 1;
                    JavadocComment javaDoc = clazz.getJavaDoc();
                    List<String> legacyProgramRefs = parseLegacyPrograms(javaDoc == null ? "" : javaDoc.getContent());
                    for (String programName : legacyProgramRefs) {
                        PCAProgram program = PCAProgram.find("byName", programName).first();
                        if (null == program) {
                            program = new PCAProgram(programName, null, author);
                            program.save();
                        }

                        PCAProgramClassLink classLink = new PCAProgramClassLink();
                        classLink.file = repoFile;
                        classLink.program = program;
                        classLink.className = clazz.getName();
                        classLink.lineTotal = classLineCount;
                        classLink.linkLines = 0;

                        classProgramTable.put(programName, classLink);

                        Logger.debug("[%d/%d] Linked class %s", filesProcessed, filesToProcess, classLink);
                    }
                } else {
                    Logger.debug("[%d/%d] Ignoring type %s", filesProcessed, filesToProcess, type.getName());
                    continue;
                }

                int numClazzLinks = classProgramTable.size();

                List<MethodDeclaration> methods = new ArrayList<MethodDeclaration>();
                List<BodyDeclaration> members = type.getMembers();
                if (null != members) {
                    for (BodyDeclaration member : members) {
                        if (member instanceof MethodDeclaration) {
                            methods.add((MethodDeclaration) member);
                        }
                    }
                }
                if (methods.isEmpty()) {
                    if (classProgramTable.isEmpty()) {
                        Logger.debug("[%d/%d] No @legacy references found in class %s", filesProcessed, filesToProcess,
                                        clazz.getName());
                    }
                    continue;
                }

                int linesAccountedFor = 0;
                List<PCAProgramMethodLink> newMethodLinks = new ArrayList<PCAProgramMethodLink>();
                for (MethodDeclaration member : methods) {
                    MethodDeclaration method = member;
                    int methodLineTotal = method.getEndLine() - method.getBeginLine() + 1;
                    JavadocComment javaDoc = method.getJavaDoc();
                    List<String> legacyProgramRefs = parseLegacyPrograms(javaDoc == null ? null : javaDoc.getContent());

                    Set<String> alreadyProcessed = new HashSet<String>();
                    for (String programName : legacyProgramRefs) {
                        if (alreadyProcessed.contains(programName)) {
                            continue;
                        }
                        alreadyProcessed.add(programName);

                        PCAProgram program = PCAProgram.find("byName", programName).first();
                        if (null == program) {
                            program = new PCAProgram(programName, null, author);
                            program.save();
                        }

                        PCAProgramMethodLink methodLink = new PCAProgramMethodLink();
                        methodLink.program = program;
                        methodLink.file = repoFile;
                        methodLink.className = clazz.getName();
                        methodLink.methodName = method.getName();
                        methodLink.lineTotal = methodLineTotal;
                        methodLink.linkLines = methodLink.lineTotal;
                        methodLink.startLine = method.getBeginLine();
                        methodLink.classLink = classProgramTable.get(programName);
                        if (null == methodLink.classLink) {
                            methodLink.classLink = indirectClassProgramTable.get(programName);
                        }
                        if (null == methodLink.classLink) {
                            methodLink.classLink = new PCAProgramClassLink();
                            methodLink.classLink.indirect = true;
                            methodLink.classLink.file = repoFile;
                            methodLink.classLink.program = program;
                            methodLink.classLink.className = clazz.getName();
                            methodLink.classLink.lineTotal = classLineCount;
                            methodLink.classLink.linkLines = 0;
                            indirectClassProgramTable.put(programName, methodLink.classLink);
                        }
                        methodLink.classLink.linkLines += methodLink.linkLines;

                        Logger.debug("Linked  method %s", methodLink);
                        if (!methodLink.isPersistent()) {
                            methodLink.classLink.save();
                        }

                        newMethodLinks.add(methodLink);
                        methodLink.save();
                    }

                    if (!alreadyProcessed.isEmpty()) {
                        linesAccountedFor += methodLineTotal;
                    }
                }

                for (PCAProgramClassLink classLink : indirectClassProgramTable.values()) {
                    Logger.debug("[%d/%d] Indirectly Linked class %s", filesProcessed, filesToProcess, classLink);
                }

                if (classProgramTable.isEmpty() && newMethodLinks.isEmpty()) {
                    Logger.debug("[%d/%d] No @legacy references found in class %s", filesProcessed, filesToProcess,
                                    clazz.getName());
                    continue;
                }

                if (0 < numClazzLinks) {
                    int remainderForDistribution = classLineCount - linesAccountedFor;
                    int perClazzCoverage = remainderForDistribution / numClazzLinks;
                    for (Entry<String, PCAProgramClassLink> entry : classProgramTable.entrySet()) {
                        PCAProgramClassLink clazzLink = entry.getValue();
                        clazzLink.linkLines += perClazzCoverage;
                        clazzLink.save();
                    }
                }
            }

            repoFile.lines = cu.getEndLine();
            repoFile.save();
        }

    }

    private static CompilationUnit loadJavaFile(final RepoFile file) {
        InputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(file.getAbsolutePath()));
            return JavaParser.parse(in);
        } catch (ParseException x) {
            Logger.warn(x, "Error parsing file %s. Is it Java code?", file.getAbsolutePath());
        } catch (FileNotFoundException e) {
            Logger.error(e, "Could not find repository file %s", file.getAbsolutePath());
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                Logger.error(e, "IO Exception procesing %s", file.getAbsolutePath());
            }
        }

        return null;
    }

    private static List<String> parseLegacyPrograms(final String comment) {
        if (null == comment) {
            return Collections.EMPTY_LIST;
        }

        List<String> programs = new ArrayList<String>();
        Matcher matcher = LEGACY_PROGRAM.matcher(comment);
        while (matcher.find()) {
            programs.add(matcher.group(1));
        }
        return programs;
    }

    /**
     * Parse program names from commit and associate {@link RepoCommit} with
     * {@link PCAProgram}.
     * 
     * @param commit
     */
    public static void linkCommitToPrograms(final RepoCommit commit) {
        Matcher matcher = COMMIT_PROGRAM.matcher(commit.message);

        // TODO: Why are some commit messages blank?
        while (matcher.find()) {
            String programName = matcher.group(1);
            PCAProgram program = PCAProgram.find("byName", programName).first();
            if (null == program) {
                continue;
            }

            commit.program = program;
            commit.save();
            Logger.debug("Linked program %s to commit %s", programName, commit.sha);
        }
    }
}
