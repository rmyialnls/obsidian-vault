---
color: var(--mk-color-brown)
sticker: emoji//1f4bb
---
### Step 1: Download the Git Installer
Copy and run this command in PowerShell to download the official Git for Windows installer directly to your Downloads folder:
```powershell
Invoke-WebRequest -Uri "https://github.com/git-for-windows/git/releases/download/v2.48.1.windows.1/Git-2.48.1-64-bit.exe" -OutFile "$env:USERPROFILE\Downloads\Git-Installer.exe"

```
![](https://i.imgur.com/PGLsbhl.png)
### Step 2: Run the Git Installer
The system cannot find the file, which means the previous step's download failed or didn't save correctly. Let's fix Step 1 by using Windows Winget to download and run it directly without needing to manage the file path.
### Step 1: Run the installation via Winget
Copy and run this command in PowerShell to instantly fetch and start the official installer:
```powershell
winget install Git.Git --interactive

```

![](https://i.imgur.com/KkUVGnD.png)
![](https://i.imgur.com/7uxUDwW.png)
![](https://i.imgur.com/sRJQjGf.png)
### Step 2: Select Components
Keep the default components selected as shown in your screenshot, and click the **Next** button to proceed.


![](https://i.imgur.com/1qXbjMH.png)


![](https://i.imgur.com/MwPf9FO.png)


\
### Step 3: Choose the Default Editor
Leave the selection as **Use Vim (the ubiquitous text editor) as Git's default editor** and click the **Next** button.
![](https://i.imgur.com/SceMsJf.png)

### Step 4: Adjust your PATH Environment
Leave the recommended selection checked: **Git from the command line and also from 3rd-party software**, then click the **Next** button.

![](https://i.imgur.com/A9ob7Uv.png)


### Step 5: Choosing the SSH Executable
Leave the default selection checked: **Use bundled OpenSSH**, then click the **Next** button.

![](https://i.imgur.com/M0UPitd.png)

### Step 6: Choosing HTTPS Transport Backend
Leave the default selection checked: **Use the native Windows Secure Channel library**, then click the **Next** button.

![](https://i.imgur.com/tUNoZv7.png)

### Step 7: Configuring the Line Ending Conversions
Leave the default selection checked: **Checkout Windows-style, commit Unix-style line endings**, then click the **Next** button.


![](https://i.imgur.com/XmK9ePX.png)

### Step 8: Configuring the Terminal Emulator to use with Git Bash
Leave the default selection checked: **Use MinTTY (the default terminal of MSYS2)**, then click the **Next** button.



![](https://i.imgur.com/UjHFonB.png)

### Step 9: Choose the Default Behavior of git pull
Leave the default selection checked: **Fast-forward or merge**, then click the **Next** button.

![](https://i.imgur.com/295SpJt.png)

### Step 10: Choose a Credential Helper
Leave the default selection checked: **Git Credential Manager**, then click the **Next** button.

![](https://i.imgur.com/jjRuqvm.png)

### Step 11: Configuring Extra Options
Leave the default selection checked: **Enable file system caching**, then click the **Install** button to begin the installation.

![](https://i.imgur.com/rnSlVyu.png)


![](https://i.imgur.com/Pkhlrhr.png)

![](https://i.imgur.com/M6QtlJN.png)


### Step 12: Verify Final Installation
Copy and run this command in PowerShell to confirm Git is installed correctly and working:
```powershell
git --version

```



![](https://i.imgur.com/CzteFq5.png)

### Step 13: Refresh Environment Variables
The installation was successful, but PowerShell needs to update its path environment variables to recognize the new command.
Copy and run this command to refresh your path without closing the window:
```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

```


![](https://i.imgur.com/CeCch9u.png)


### Step 14: Confirm Git Recognition
Copy and run this command to verify that the path refresh worked and Git is now fully recognized by PowerShell:
```powershell
git --version

```

![](https://i.imgur.com/ircPNOW.png)

### Step 15: Installation Complete
Your terminal output shows git version 2.54.0.windows.1. The installation is successful and Git is fully operational.

Got it. Now that Git is installed on your laptop, the first step is to create a secure storage spot online so both devices can see your notes.
We will use **GitHub** for this because it is free, secure, and integrates with Obsidian on both Windows and mobile.
### Step 1: sign in to GitHub Account
 1. Open your browser and go to github.com.
 2.  sign in 
Looking at your screenshot "1780191271350.png", you nailed it. **rmyialnls** is exactly your GitHub username.
Here are your personalized commands ready to go. Copy and run them one at a time in your PowerShell window:
### 1. Set your username
```powershell
git config --global user.name "rmyialnls"

```
### 2. Set your email
```powershell
git config --global user.email "rmyialnls@gmail.com"

```
Let me know once both are entered, and we will move to the next step!

![](https://i.imgur.com/AT39EVk.png)



### Step 4: Navigate PowerShell to your local Obsidian Vault folder
We need to tell PowerShell to look inside your actual Obsidian vault folder on your laptop.
To do this, you need to use the cd (change directory) command, followed by the path to where your vault is saved. For example, if your vault is in your Documents folder, the command will look something like this:
```powershell
cd "C:\Users\rmyia\Documents\YourObsidianVaultName"

```


To help you find the exact path for the first vault on your laptop, open your **Obsidian** app on your laptop and look at the bottom-left corner for the vault name.
Hover your mouse over that vault name or click the folder icon to see the full folder path (it will show something like C:\Users\rmyia\...).
What is the full path or name of the first vault you want to sync?

![](https://i.imgur.com/dNb2Iyp.png)

My apologies. I will not mention or write out the image filenames again.
Let's try that step one more time. Copy and paste this exact command into PowerShell and press **Enter**:
```powershell
cd "C:\Users\rmyia\Documents\Rainman"

```

![](https://i.imgur.com/yLtNKb7.png)


That worked perfectly. Your PowerShell is now active inside the correct folder.
### Step 6: Initialize Git in your folder
Now we need to tell Git to turn this folder into a local repository. Copy and paste this command, then press **Enter**:
```powershell
git init

```


![](https://i.imgur.com/rqplg6A.png)

Excellent, the empty Git repository has been created.
### Step 7: Group all your existing notes together
Next, we need to stage all the files inside this folder so Git knows it should track them. Copy and paste this command, including the period at the end, then press **Enter**:
```powershell
git add .

```

![](https://i.imgur.com/cceIwPK.png)

Those warnings are totally normal for Windows—it just means Git successfully staged your files.
### Step 8: Save your first snapshot (Commit)
Now we need to permanently lock these files into your first local snapshot. Copy and paste this command and press **Enter**:
```powershell
git commit -m "Initial commit of Rainman vault"

```

![](https://i.imgur.com/bikbHke.png)

The commit completed perfectly. All your files are officially tracked locally.
### Step 9: Set the main branch name
Now we need to name our primary tracking branch "main". Copy and paste this command, then press **Enter**:
```powershell
git branch -M main

```

![](https://i.imgur.com/ZZWqg1R.png)

The branch name is set. Now we need to link this local folder to the blank repository you created on GitHub.
### Step 10: Link your local repository to GitHub
Copy and paste this command into PowerShell and press **Enter**:
```powershell
git remote add origin https://github.com/rmyialnls/obsidian-vault.git

```

![](https://i.imgur.com/Ja7y2sK.png)

The link command went through cleanly. Now it's time to push your local files up to GitHub for the first time.
### Step 11: Push your files to GitHub
Copy and paste this command into PowerShell and press **Enter**:
```powershell
git push -u origin main

```
*(Note: Since this is your first time pushing from this laptop, Windows might pop up a small window asking you to sign into GitHub to authorize the connection. If that happens, just click "Sign in with your browser" and approve it).*

![](https://i.imgur.com/9a17gyt.png)


Ah, it looks like Git ran into a "Repository not found" error because the remote URL doesn't perfectly match a repository on your GitHub account yet. We just need to fix that link.
### Step 12: Remove the incorrect link
Before we add the correct one, we need to clear out the old link. Copy and paste this command into PowerShell and press **Enter**:
```powershell
git remote remove origin

```

![](https://i.imgur.com/L6JhRqn.png)

Perfect. Now that you are on the page, you just need to fill in the name and click create.
### Step 14: Name the repository
Copy this snippet and paste it directly into the **Repository name** box on the webpage:
```text
obsidian-vault

```
### Step 15: Create it
 1. Scroll all the way to the bottom of that GitHub page.
 2. Click the green **Create repository** button.
Once you click it, the page will refresh and show a screen with some code text. Let me know when you see that screen!
Look at the top-right corner of your screen. Click the **+ (plus sign)** icon next to your profile picture, then click **New repository** from the drop-down menu.
Let me know once you have clicked that.

![](https://i.imgur.com/6ppKEDR.png)


You are exactly where you need to be.
### Next Step: Enter the Name
Copy this snippet and paste it into the empty **Repository name** box on that page:
```text
obsidian-vault

```
Let me know once you have pasted that in.


![](https://i.imgur.com/XIXfNM3.png)


![](https://i.imgur.com/Y2f9vVp.png)

Perfect, the name is in.
### Next Step: Create the Repository
Look at the very bottom of that page and click the green **Create repository** button.
Let me know when the page changes!

![](https://i.imgur.com/LjQoJs2.png)

Beautiful, the repository is created and ready to receive your files.
Now we just need to head back over to your PowerShell window to link everything up and push the files.
### Next Step: Link the repository in PowerShell
Copy and paste this command into your PowerShell window and press **Enter**:
```powershell
git remote add origin https://github.com/rmyialnls/obsidian-vault.git

```


![](https://i.imgur.com/uADyjhn.png)

The link went through smoothly. Now we can finally send the files to your new GitHub repository.
### Next Step: Push the files
Copy and paste this final command into PowerShell and press **Enter**:
```powershell
git push -u origin main

```


![](https://i.imgur.com/fD5FBVu.png)

![](https://i.imgur.com/aRNgeDR.png)

![](https://i.imgur.com/HChj1wX.png)

![](https://i.imgur.com/haH2kxR.png)
The authentication went through perfectly, but GitHub blocked the upload because it detected active login credentials inside your Google Calendar plugin settings file (data.json). GitHub automatically stops the upload to protect your account keys from being exposed online.
To fix this, we need to tell Git to ignore that specific data file so it doesn't try to upload your private login details.
### Next Step: Tell Git to ignore the secret file
Copy and paste this command into PowerShell and press **Enter**:
```powershell
Add-Content .gitignore "`n.obsidian/plugins/google-calendar/data.json"

```

![](https://i.imgur.com/3YtvzPH.png)

Because that secret file was already included in your previous save (commit), we have to untrack it from Git's memory so it actually respects the ignore rule.
### Next Step: Untrack the data file
Copy and paste this command into PowerShell and press **Enter**:
```powershell
git rm --cached .obsidian/plugins/google-calendar/data.json

```

![](https://i.imgur.com/ac1qUss.png)


That successfully untracked the secret file. Now we need to save this update to your repository history.
### Next Step: Save the update
Copy and paste this command into PowerShell and press **Enter**:
```powershell
git commit -m "Ignore calendar secrets"

```

![](https://i.imgur.com/UmkrhSl.png)

The update is saved and the credentials are completely cleared out of the upload bundle. We are ready to send it to GitHub again.
### Next Step: Push the files to GitHub
Copy and paste this command into PowerShell and press **Enter**:
```powershell
git push -u origin main

```

![](https://i.imgur.com/dDlN2Dg.png)

GitHub blocked it again because that secret data is still hidden deep in your older history (the first save we made). To completely clean the history so GitHub accepts the upload, we need to reset our local save point and make a fresh start.
### Next Step: Reset the Git tracking history
Copy and paste this command into PowerShell and press **Enter**:
```powershell
Remove-Item -Path .git -Recurse -Force

```

![](https://i.imgur.com/TbYmYIp.png)

Now that we have removed the old tracking history, we just need to initialize a clean, fresh Git link for your project.
### Next Step: Initialize a fresh Git setup
Copy and paste this command into PowerShell and press **Enter**:
```powershell
git init

```

![](https://i.imgur.com/7b7WnOC.png)

Perfect, the fresh tracking history is started. Now we need to select all your files for this clean setup.
### Next Step: Select all files
Copy and paste this command into PowerShell and press **Enter**:
```powershell
git add .

```











