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

import javax.persistence.FlushModeType;

import models.PCAProgram;
import models.PCAProgramClassLink;
import models.PCAProgramMethodLink;
import models.RepoCommit;
import models.RepoFile;
import play.Logger;

public class PCALinkageService {

    private static Pattern LEGACY_PROGRAM = Pattern.compile("@legacy[\\s]*([a-zA-Z][a-zA-Z0-9]{2,7})(\\s|$)");
    private static Pattern COMMIT_PROGRAM = Pattern.compile("(^|\\W)([A-Z][A-Z0-9]{2,7})(\\W|$)");

    /**
     * Identify all the RepoFiles that are not fresh with respect to recent
     * commits and update all their PCA class/method links.
     * 
     * @return number of files processed
     */
    public static int updateFileLinkage() {
        List<RepoFile> filesToLink = RepoFile.find("byLinkUpdateNeeded", Boolean.TRUE).fetch();

        int filesToProcess = filesToLink.size();
        int filesProcessed = 0;
        long start = System.currentTimeMillis();

        // Speed things up?
        PCAProgramClassLink.em().setFlushMode(FlushModeType.COMMIT);

        for (RepoFile repoFile : filesToLink) {
            filesProcessed++;

            // Drop all existing links
            PCAProgramClassLink.delete("file = ?", repoFile);

            String path = repoFile.getAbsolutePath();
            File file = new File(path);

            if (!file.exists()) {
                Logger.debug("[%d/%d] Unlinked deleted repository file %s", filesProcessed, filesToProcess,
                                file.getAbsoluteFile());

                repoFile.linkUpdateNeeded = Boolean.FALSE;
                repoFile.deletedInHead = Boolean.TRUE;
                repoFile.save();
                continue;
            }

            repoFile.deletedInHead = Boolean.FALSE;

            CompilationUnit cu = loadJavaFile(repoFile);

            List<TypeDeclaration> types = cu.getTypes();
            if (null == types) {
                Logger.warn("[%d/%d] File %s has no type declaration.", filesProcessed, filesToProcess, path);

                repoFile.linkUpdateNeeded = Boolean.FALSE;
                repoFile.save();
                continue;
            }

            String author = null;
            for (TypeDeclaration type : types) {
                String typeName = null;
                int classLineCount = 0;

                Map<String, PCAProgramClassLink> classProgramTable = new HashMap<String, PCAProgramClassLink>();
                Map<String, PCAProgramClassLink> indirectClassProgramTable = new HashMap<String, PCAProgramClassLink>();

                if (type instanceof ClassOrInterfaceDeclaration || type instanceof EnumDeclaration) {
                    typeName = type.getName();
                    classLineCount = type.getEndLine() - type.getBeginLine() + 1;
                    JavadocComment javaDoc = type.getJavaDoc();
                    Set<String> legacyProgramRefs = parseLegacyPrograms(javaDoc == null ? "" : javaDoc.getContent());
                    for (String programName : legacyProgramRefs) {
                        PCAProgram program = PCAProgram.findById(programName);
                        if (null == program) {
                            continue;
                        }

                        PCAProgramClassLink classLink = new PCAProgramClassLink();
                        classLink.file = repoFile;
                        classLink.program = program;
                        classLink.className = type.getName();
                        classLink.lineTotal = classLineCount;
                        classLink.linkLines = 0;

                        classProgramTable.put(programName, classLink);

                        flagProgramForAuthorUpdate(program);

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
                                        typeName);
                    }
                    continue;
                }

                int linesAccountedFor = 0;
                List<PCAProgramMethodLink> newMethodLinks = new ArrayList<PCAProgramMethodLink>();
                for (MethodDeclaration member : methods) {
                    MethodDeclaration method = member;
                    int methodLineTotal = method.getEndLine() - method.getBeginLine() + 1;
                    JavadocComment javaDoc = method.getJavaDoc();
                    Set<String> legacyProgramRefs = parseLegacyPrograms(javaDoc == null ? null : javaDoc.getContent());

                    Set<String> alreadyProcessed = new HashSet<String>();
                    for (String programName : legacyProgramRefs) {
                        if (alreadyProcessed.contains(programName)) {
                            continue;
                        }
                        alreadyProcessed.add(programName);

                        PCAProgram program = PCAProgram.findById(programName);
                        if (null == program) {
                            continue;
                        }

                        PCAProgramMethodLink methodLink = new PCAProgramMethodLink();
                        methodLink.program = program;
                        methodLink.file = repoFile;
                        methodLink.className = typeName;
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
                            methodLink.classLink.className = typeName;
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

                        flagProgramForAuthorUpdate(program);
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
                                    typeName);
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
            repoFile.linkUpdateNeeded = Boolean.FALSE;
            repoFile.save();

            if (filesProcessed % 100 == 0) {
                logTimingStatus(filesProcessed, start);
            }
        }

        logTimingStatus(filesProcessed, start);
        return filesProcessed;
    }

    private static void flagProgramForAuthorUpdate(final PCAProgram program) {
        if (!program.needsAuthorUpdate) {
            program.needsAuthorUpdate = true;
            program.save();
        }
    }

    private static void logTimingStatus(final int filesProcessed, final long start) {
        if (filesProcessed > 0) {
            long stop = System.currentTimeMillis();
            float time = (stop - start) / 1000f;
            float average = time / filesProcessed;

            Logger.info("Processed %d files in %f seconds. Average %f", filesProcessed, time, average);
        }
    }

    /**
     * Initialize a class link id selection by choosing only the ids out of the
     * given linkList where the linkList has adequate program coverage.
     * 
     * @param linkList
     * @return List of PCAProgramClassLink ids.
     */
    public static List<Long> defaultClassSelection(final List<PCAProgramClassLink> linkList) {
        List<Long> selection = new ArrayList<Long>();
        for (PCAProgramClassLink link : linkList) {
            if (link.lineCoverage() > 60f) {
                selection.add(link.getId());
            }
        }
        return selection;
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

    private static Set<String> parseLegacyPrograms(final String comment) {
        if (null == comment) {
            return Collections.EMPTY_SET;
        }

        Set<String> programs = new HashSet<String>();
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

        while (matcher.find()) {
            String programName = matcher.group(2);
            PCAProgram program = PCAProgram.findById(programName);
            if (null == program) {
                continue;
            }

            commit.program = program;
            commit.save();

            flagProgramForAuthorUpdate(program);

            Logger.debug("Linked program %s to commit %s", programName, commit.sha);
        }
    }

    public static void wipeAllLinks() {
        PCAProgramClassLink.deleteAll();
        RepoFile.em().createQuery("UPDATE RepoFile SET linkUpdateNeeded = true").executeUpdate();
    }
}
