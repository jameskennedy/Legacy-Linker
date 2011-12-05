package models;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Transient;

import org.apache.commons.lang.StringUtils;
import org.hibernate.annotations.Index;

import play.data.validation.Required;
import play.data.validation.Unique;
import play.db.jpa.Model;
import edu.nyu.cs.javagit.api.commands.GitLogResponse.Commit;

@Entity
public class RepoCommit extends Model {

    @Transient
    private static final String SVN_REV_TOKEN = "git-svn-id:";
    @Unique
    @Index(name = "shaIndex")
    @Required
    public String sha;
    public Long svnRevision;
    public String svnURL;
    public String author;
    @Required
    @Lob
    public String message;
    @Required
    public Date date;

    public static SimpleDateFormat DF = new SimpleDateFormat("E MMM d HH:mm:ss yyyy Z");

    public RepoCommit(final Commit gitCommit) throws ParseException {
        sha = gitCommit.getSha();
        author = gitCommit.getAuthor();
        message = gitCommit.getMessage().trim();
        parseSVNRevision();
        // Expecting format: Sat Dec 3 23:58:57 2011 +0000
        date = DF.parse(gitCommit.getDateString());
    }

    @Override
    public String toString() {
        return "RepoCommit [sha=" + sha + ", date=" + date + ", svnRevision=" + svnRevision + ", author=" + author
                        + ", message=" + StringUtils.abbreviate(message, 20) + "]";
    }

    private void parseSVNRevision() {
        int index = message.indexOf(SVN_REV_TOKEN);
        if (index == -1) {
            return;
        }

        svnURL = message.substring(index + SVN_REV_TOKEN.length() + 1, message.lastIndexOf(" "));
        message = message.substring(0, index).trim();

        index = svnURL.lastIndexOf("@");
        svnRevision = Long.parseLong(svnURL.substring(index + 1));
    }

    /**
     * Determine if this commit has been pushed to a remote repository.
     * 
     * @return true if shared remotely
     */
    public boolean sharedInRemoteRepository() {
        // TODO: Current implementation assuming SVN bridge
        return svnRevision != null;
    }

}
