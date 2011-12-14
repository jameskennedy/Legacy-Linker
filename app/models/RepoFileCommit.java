package models;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import play.db.jpa.Model;

@Entity
public class RepoFileCommit extends Model implements Comparable<RepoFileCommit> {

    @ManyToOne()
    public RepoFile file;
    @ManyToOne()
    public RepoCommit commit;

    public Integer linesAdded;
    public Integer linesRemoved;

    @Override
    public int compareTo(final RepoFileCommit other) {
        return this.commit.compareTo(other.commit);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((commit == null) ? 0 : commit.hashCode());
        result = prime * result + ((file == null) ? 0 : file.hashCode());
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
        RepoFileCommit other = (RepoFileCommit) obj;
        if (commit == null) {
            if (other.commit != null) {
                return false;
            }
        } else if (!commit.equals(other.commit)) {
            return false;
        }
        if (file == null) {
            if (other.file != null) {
                return false;
            }
        } else if (!file.equals(other.file)) {
            return false;
        }
        return true;
    }

}
