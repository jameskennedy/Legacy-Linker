package models;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.apache.commons.lang.StringUtils;
import org.hibernate.annotations.Index;

import play.data.validation.Required;
import play.data.validation.Unique;
import play.db.jpa.Model;
import edu.nyu.cs.javagit.api.commands.GitLogResponse.Commit;

@Entity
public class RepoCommit extends Model implements Comparable<RepoCommit> {

    @Transient private static final String SVN_REV_TOKEN = "git-svn-id:";
    @Unique @Index(name = "shaIndex") @Required public String sha;
    public Integer svnRevision;
    public String svnURL;
    public String author;
    @Required @Lob public String message;
    @Required public Date date;
    @ManyToOne public PCAProgram program;
    public Integer linesAdded;
    public Integer linesRemoved;

    @Transient public String toolTip;

    public static SimpleDateFormat DF = new SimpleDateFormat("E MMM d HH:mm:ss yyyy Z");

    public RepoCommit(final Commit gitCommit) throws ParseException {
        sha = gitCommit.getSha();
        author = gitCommit.getAuthor();
        author = author.substring(0, author.indexOf(" "));
        message = gitCommit.getMessage() == null ? "[No message]" : gitCommit.getMessage().trim();
        parseSVNRevision();
        // Expecting format: Sat Dec 3 23:58:57 2011 +0000
        date = DF.parse(gitCommit.getDateString());
        linesAdded = gitCommit.getLinesInserted();
        linesRemoved = gitCommit.getLinesDeleted();
    }

    public String getToolTip() {
        return toolTip;
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
        svnRevision = Integer.parseInt(svnURL.substring(index + 1));
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((sha == null) ? 0 : sha.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        RepoCommit other = (RepoCommit) obj;
        if (sha == null) {
            if (other.sha != null) {
                return false;
            }
        } else if (!sha.equals(other.sha)) {
            return false;
        }
        return true;
    }

    /**
     * Reverse chronological order.
     */
    @Override
    public int compareTo(final RepoCommit other) {
        return other.date.compareTo(this.date);
    }

}
