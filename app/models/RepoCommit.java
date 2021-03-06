package models;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Transient;

import org.apache.commons.lang.StringUtils;

import play.data.validation.Required;
import play.db.jpa.GenericModel;
import edu.nyu.cs.javagit.api.commands.GitLogResponse.Commit;

@Entity
public class RepoCommit extends GenericModel implements Comparable<RepoCommit> {

    private static final int MAX_MSG_LENGTH = 2000;

    @Transient
    private static final String SVN_REV_TOKEN = "git-svn-id:";

    @Id
    public String sha;
    @Required
    @Column(length = MAX_MSG_LENGTH)
    public String message;
    @Required
    public Date date;
    public Integer svnRevision;
    public String svnURL;
    @Required
    @ManyToOne
    public User user;
    public String author;
    @ManyToOne
    public PCAProgram program;
    public String programName;
    public Integer linesAdded;
    public Integer linesRemoved;

    @OneToMany(mappedBy = "commit", cascade = { CascadeType.ALL })
    @OrderBy("linesAdded DESC")
    List<RepoFileCommit> files;

    @Transient
    public String toolTip;

    public static SimpleDateFormat DF = new SimpleDateFormat("E MMM d HH:mm:ss yyyy Z");

    @Override
    public Object _key() {
        return sha;
    }

    public RepoCommit(final Commit gitCommit) throws ParseException {
        sha = gitCommit.getSha();

        String author = gitCommit.getAuthor();
        String email = null;
        int index = author.indexOf(" ");
        if (index != -1) {
            email = author.substring(index + 1);
            author = author.substring(0, index);
        }
        user = User.find("byRepoUserId", author).first();
        if (null == user) {
            user = new User(author, email);
            user.save();
        }

        message = gitCommit.getMessage() == null ? "[No message]" : gitCommit.getMessage().trim();
        parseSVNRevision();
        if (message.length() > 2000) {
            message = message.substring(0, MAX_MSG_LENGTH);
        }
        // Expecting format: Sat Dec 3 23:58:57 2011 +0000
        date = DF.parse(gitCommit.getDateString());
        linesAdded = gitCommit.getLinesInserted();
        linesRemoved = gitCommit.getLinesDeleted();
    }

    public String getToolTip() {
        return toolTip;
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
    public String toString() {
        return "RepoCommit [sha=" + sha + ", date=" + date + ", svnRevision=" + svnRevision + ", author=" + user
                        + ", message=" + StringUtils.abbreviate(message, 20) + "]";
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
