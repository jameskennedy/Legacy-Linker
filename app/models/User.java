package models;

import javax.persistence.Column;
import javax.persistence.Entity;

import play.data.validation.Email;
import play.data.validation.Required;
import play.data.validation.Unique;
import play.db.jpa.Model;

@Entity
public class User extends Model implements Comparable<User> {
    @Unique
    @Required
    @Column(updatable = false)
    public String repoUserId;
    public String firstName;
    public String lastName;
    @Email
    public String email;

    public User(final String author, final String email) {
        this.repoUserId = author;
        this.email = email;
    }

    @Override
    public String toString() {
        return shortName();
    }

    public String getDisplayName() {
        if (null == firstName && null == lastName) {
            return repoUserId;
        }

        String first = firstName == null ? "" : firstName;
        String last = lastName == null ? "" : lastName;

        return String.format("%s %s (%s)", first, last, repoUserId);
    }

    public String shortName() {
        if (null == firstName) {
            return repoUserId;
        }

        String initial = lastName != null && lastName.length() > 0 ? lastName.substring(0, 1).toUpperCase() : "";
        return firstName + initial;
    }

    @Override
    public int compareTo(final User other) {
        if (other == null) {
            return -1;
        }
        return this.getDisplayName().compareTo(other.getDisplayName());
    }
}
