# How to Submit Cyclic Alarms to F-Droid

Follow these steps in order. Each step is small and has a screenshot or clear description.

---

## PART 1 — Put your source code on GitHub (public)

F-Droid builds from your public source code. Your repo must be visible to everyone.

1. Go to https://github.com and sign up (or sign in if you have an account).

2. Click the **+** button (top-right) → **New repository**.

3. Fill in:
   - Repository name: `cyclic-alarms`
   - Description: `Alarm app for shift schedules — repeats every N days`
   - Visibility: **Public** ← this is required
   - Do NOT tick "Add a README" (you already have one)

4. Click **Create repository**.

5. GitHub will show you a page with commands. Copy the URL shown — it looks like:
   `https://github.com/YOUR_USERNAME/cyclic-alarms.git`

6. Open a terminal in your project folder and run these commands one by one:
   ```
   git remote add origin https://github.com/YOUR_USERNAME/cyclic-alarms.git
   git branch -M main
   git push -u origin main
   ```
   (Replace YOUR_USERNAME with your actual GitHub username)

7. Refresh the GitHub page — you should see all your files there.

8. **Tag the release** (F-Droid uses tags to find versions):
   ```
   git tag v1.6
   git push origin v1.6
   ```

---

## PART 2 — Update the metadata file with your real GitHub URL

Open the file `fdroid/com.beemasfincon.cyclicalarms.yml` in this project.

Replace every occurrence of:
```
YOUR_GITHUB_USERNAME
```
with your actual GitHub username (e.g. `pramodbeema` or whatever you chose).

---

## PART 3 — Create a GitLab account and fork fdroiddata

F-Droid uses GitLab (not GitHub) for their app submissions.

1. Go to https://gitlab.com and sign up for a free account.

2. Go to https://gitlab.com/fdroid/fdroiddata

3. Click the **Fork** button (top-right area of the page).
   - This creates your own copy of the F-Droid app database.

4. Wait a moment — GitLab will redirect you to your fork at:
   `https://gitlab.com/YOUR_GITLAB_USERNAME/fdroiddata`

---

## PART 4 — Add your app's metadata file to the fork

1. In your forked fdroiddata repo on GitLab, navigate to the folder called `metadata/`.

2. Click the **+** button → **New file**.

3. For the filename, type exactly:
   ```
   com.beemasfincon.cyclicalarms.yml
   ```

4. Copy the entire contents of the file `fdroid/com.beemasfincon.cyclicalarms.yml`
   from this project and paste it into the GitLab editor.

5. Make sure YOUR_GITHUB_USERNAME has been replaced with your real GitHub username.

6. Scroll down, add a commit message like:
   ```
   Add com.beemasfincon.cyclicalarms (Cyclic Alarms)
   ```

7. Click **Commit changes**.

---

## PART 5 — Open a Merge Request (this is the actual submission)

1. After committing, GitLab will show a banner saying
   "You pushed to a branch — create a merge request". Click it.

2. Fill in:
   - Title: `Add com.beemasfincon.cyclicalarms`
   - Description: write a short sentence like:
     ```
     Cyclic Alarms is a local alarm app for shift-work schedules.
     No ads, no tracking, no proprietary dependencies.
     Source: https://github.com/YOUR_USERNAME/cyclic-alarms
     ```

3. Click **Create merge request**.

That's it — your submission is in. F-Droid reviewers will check it and may
leave comments asking for small fixes. Check the merge request page every few
days for replies. The review process typically takes 2–8 weeks.

---

## What NOT to do

- Do NOT share your keystore file or passwords with anyone.
- Do NOT commit `local.properties` (it's already in .gitignore — you're safe).
- Do NOT commit `google-services.json` (deleted and gitignored — you're safe).

---

## Questions?

F-Droid community forum: https://forum.f-droid.org
F-Droid submission docs: https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
