package services;

import java.util.HashMap;

import models.GITRepository;
import models.PCAProgramClassLink;
import models.RepoCommit;
import models.RepoFile;
import models.RepoFileCommit;
import models.User;
import play.Logger;

public class RepositoryService {

    public static void wipeRepositoryData() {
        // Wipe dependent data
        LegacyLinkageService.wipeAllLinks();

        RepoFileCommit.deleteAll();
        RepoFile.deleteAll();
        RepoCommit.deleteAll();
        GITRepository mainRepository = GITRepository.getMainRepository();
        mainRepository.earliestCommitDate = null;
        mainRepository.lastCommitDate = null;
        mainRepository.lastCommitParsed = null;
        mainRepository.save();
        Logger.info("Wiped all repository data.");
    }

    /**
     * Tally the number of lines touched for each author. The line score is
     * number of lines added + number of lines removed.
     * 
     * TODO: More accurately use exactly the lines covered under @legacy for the
     * given program e.g. git blame -p -w -C -M -l -L1,100
     * 
     * @param classLink
     */
    public static void calculateAuthorship(final PCAProgramClassLink classLink) {
        if (classLink.authorLinesMap != null) {
            return;
        }

        classLink.authorLinesMap = new HashMap<User, Integer>();

        RepoFile file = classLink.file;

        for (RepoFileCommit fileCommit : file.commits) {
            User author = fileCommit.commit.user;
            Integer commitLines = fileCommit.linesAdded + fileCommit.linesRemoved;
            Integer authorLines = classLink.authorLinesMap.get(author);
            if (null == authorLines) {
                authorLines = 0;
            }
            authorLines = authorLines + commitLines;
            classLink.authorLinesMap.put(author, authorLines);
        }
    }

}
