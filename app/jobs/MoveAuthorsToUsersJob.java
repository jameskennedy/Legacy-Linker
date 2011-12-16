package jobs;

import models.RepoCommit;
import models.User;
import play.jobs.Job;

/**
 * This job updates the author info of programs if they need it.
 * 
 * @author james.kennedy
 * 
 */
public class MoveAuthorsToUsersJob extends Job {

    @Override
    public void doJob() {
        for (RepoCommit commit : RepoCommit.<RepoCommit> findAll()) {
            String author = commit.author;
            String email = null;
            int index = author.indexOf(" ");
            if (-1 != index) {
                email = author.substring(index + 1);
                author = author.substring(0, index);
            }
            User user = User.find("byRepoUserId", author).first();
            if (null == user) {
                user = new User(author, email);
                user.save();
            }

            System.out.println(commit.date);

            commit.user = user;
            commit.save();
        }
    }
}
