---
color: var(--mk-color-brown)
sticker: emoji//1f4bb
---
# n8n and docker set up instructions

## Step 1   
To begin the clean slate for your  [[n8n]]instance, we must first ensure the existing container and its configuration are removed.

**Step 1:** Run these commands in your PowerShell/Terminal to stop and remove the current n8n instance:

```powershell

docker stop n8n

docker rm n8n

  

```

Confirm that these commands have completed successfully, then provide the screenshot for this step.

![](https://i.imgur.com/KA39We9.png)

Yes, that is perfect. Go ahead and click **"Paste anyway"**.
Once it runs, it will likely give you an error message if the container was already gone, or it will simply return to the command prompt if it successfully stopped and removed it.
Please paste the output (or a screenshot of the output) so I can verify that the n8n container is gone and we can move to the next step.

## Step 2

Perfect, the container is successfully stopped and removed.
Now, we need to clean up the Docker volume that stored your n8n data. This ensures that when we reinstall, we are truly starting from scratch.
**Step 2:** Run this command in PowerShell to list your volumes and identify the correct one to delete:
```powershell
docker volume ls

```
Once you run that, please show me the output (or a screenshot) so I can verify the volume name before we remove it.

![](https://i.imgur.com/MlMJdSV.png)


## Step 3

![](https://i.imgur.com/mHx5txB.png)

It looks like you have n8n_data and n8n_data_permanent in that list. Since we want a completely fresh start for n8n, we should remove those.
**Step 3:** Run this command to remove those two volumes:
```powershell
docker volume rm n8n_data n8n_data_permanent

```
Once that runs, please show me the output so I can confirm they are gone, and then we will be ready to set up the clean n8n installation.

## Step 4

![](https://i.imgur.com/ErOgidY.png)

Great, those volumes are successfully removed. Now that the old data is cleared, we are ready to install a fresh, clean instance of n8n.
**Step 4:** Run this command to create and start your new n8n container. I've set it up to map your data to a folder named n8n_data in your current directory, which will make it very easy to manage and back up later.
```powershell
docker run -d --name n8n -p 5678:5678 -v ${PWD}/n8n_data:/home/node/.n8n --restart unless-stopped docker.n8n.io/n8nio/n8n

```
*Note: I added --restart unless-stopped, which is a great first step toward your goal of self-sufficiency—this tells Docker to automatically restart n8n if your computer reboots or if the container crashes.*
Once you run this, please send the screenshot showing that it started successfully.

## Step 5

![](https://i.imgur.com/wiTlz6i.png)


It looks like the container started successfully, as indicated by the long string of characters (the container ID) returned in your PowerShell window.
Now, let's verify that it is running correctly.
**Step 5:** Run this command to check the status of your containers:
```powershell
docker ps

```
This will show you a table. Please send a screenshot of the output so I can confirm it's up and running, and then we will move on to testing the access.

## Step 6

![](https://i.imgur.com/yGmn748.png)


## Step 7a

![](https://i.imgur.com/s9Li5uK.png)


### Error 

![](https://i.imgur.com/3SClr3f.png)


![](https://i.imgur.com/R4vJ7je.png)

That error is usually a temporary browser caching issue where your browser is trying to load old UI components from the previous installation instead of the fresh files from the new one. Since we just deleted everything, a quick "hard refresh" should fix it.
**Step 8: Perform a hard refresh**
 1. Keep your browser tab open at http://localhost:5678/setup.
 2. Press **Ctrl + F5** (or Ctrl + Shift + R) on your keyboard. This forces your browser to ignore the cache and reload all the files fresh from the n8n container.
 3. Once it reloads, try entering your information again and click "Next".
If that works and you get to the dashboard, please send me a screenshot of the main screen so we can confirm you're in!
control F5 worked
Since you have saved all the necessary command blocks and steps in your Obsidian, you are safe to close that PowerShell window. The container n8n is managed by the Docker Engine, so they will continue running in the background regardless of whether that specific terminal window is open.

## Step 7b
Sign in
Email enter rmyialnls@gmail.com 
First Name enter: Ryan 
Last name enter: Mills 
Password enter: Ryanm!11$

![](https://i.imgur.com/BE3e8IY.png)


Enter information

Enter: Marketing agency/consultancy
Enter: Sales and Marketing
Enter: Lead generation, enrichment, routing
Click continue



![](https://i.imgur.com/FG6AYhy.png)

![](https://i.imgur.com/YQ5e72r.png)

## Step 8

![](https://i.imgur.com/aeBbhLf.png)

Enter licensing key.
### Step 8a
After setting up N8N and signing in. a table will pop up asking if you want advanced features. click yes and a licensing will be sent to your Gmail. 

Open Gmail.  https://mail.google.com/mail/u/0/#inbox
Sign in to  Gmail  
username: rmyialnls@gmail.com 
password: Ryanm!11$

Open new email from n8n.  https://mail.google.com/mail/u/0/#inbox/FMfcgzQgMCWGZfBtxqQGDBdJwLWKqCJHClick activate license. 
sign in to n8n. http://localhost:5678/signin?redirect=%252Fsettings%252Fusage%253Fkey%253D3c4a9753-21e2-4646-bd81-910cf12d2f51

![](https://i.imgur.com/BE3e8IY.png)

Email rmyialnls@gmail.com 
Password: Ryanm!11$

Email enter rmyialnls@gmail.com 
First Name enter: Ryan 
Last name enter: Mills 
Password enter: Ryanm!11$

### Step 8b
Or to enter your license key, you need to navigate to the settings within your n8n interface. Since it's currently trying to open in Microsoft Edge (which is likely not where your n8n is "active"), follow these steps to do it directly in the browser where your n8n dashboard is running.
### 1. Locate the Activation Menu
 1. Look at the **left-hand sidebar** of your n8n dashboard (where you are currently logged in).
 2. At the very bottom of that sidebar, you should see an icon that looks like a **gear (Settings)**.
 3. Click on the **Settings** icon.
### 2. Enter the Key
 1. Once inside Settings, look for a menu item labeled **"License"** or **"Usage and Plan"**.
 2. There should be a field where you can paste your license key.
 3. After pasting it, click the **Activate** button.
**If the button still tries to open a new window in Edge:**
Instead of clicking the link or button that triggers the browser, simply **right-click** the link and select **"Copy link address"**, then paste that link directly into the address bar of the browser where you are already logged into n8n.
Once that is done, your n8n instance will be fully activated. Since we have completed the clean installation and verified access, is there a specific workflow you would like to start building, or would you like to move on to the next component of your system?



## Step 9: Install Ollama Locally on Windows
This step sets up the local engine on your computer so you can manage your AI models directly.
 1. Visit the official download page: https://ollama.com/download/windows.
 2. Click **"Download for Windows"** to get the OllamaSetup.exe file.
 3. Double-click the installer and follow the prompts to complete the installation.
 4. Once finished, open a new PowerShell window and verify the installation by typing:
   ```powershell
   ollama --version
   
   ```
 5. To download your chosen AI model (e.g., Llama 3.2), run:
   ```powershell
   ollama run llama3.2
   
   ```
   *(This command will automatically download the model and launch a chat interface to verify it is working. You can type /bye to exit the chat.)*

## Step 10
We need to set up Ollama in Docker and then connect n8n to it.
Since we are doing this one step at a time, let's start by getting Ollama running in its own container.

Run this command in your PowerShell to start the Ollama container:
```powershell
docker run -d --name ollama -p 11434:11434 -v ${PWD}/ollama_data:/root/.ollama --restart unless-stopped ollama/ollama

```
**What this does:**
 * **-d**: Runs it in the background.
 * **-p 11434:11434**: Opens the port n8n needs to talk to Ollama.
 * **-v ${PWD}/ollama_data:/root/.ollama**: Saves your AI models to a folder called ollama_data in your current directory so they persist.
 * **--restart unless-stopped**: Ensures it stays running automatically, just like your n8n setup.
Once you run this, please take a screenshot of the output so I can confirm it's running, and then we'll move to the next part: pulling an AI model to use.

You are absolutely right to think about the workflow for your Obsidian manual.
To clarify how this works: **You do not need to download the model separately to your computer first.**
When you run the command docker exec -it ollama ollama run hermes3 inside your Docker container, the container **automatically** contacts the Ollama library on the internet, downloads the model file, and saves it *inside* the ollama_data folder we created on your actual computer.
So, this **one step** (Step 11) actually handles both the "downloading" and "putting it into the container" parts automatically. It is a very efficient process.
![](https://i.imgur.com/4g6hiip.png)

### Step 11: Pull and Run Hermes 3
Since your Ollama Docker container is already running, run this command in your PowerShell to download and activate the Hermes 3 model inside it:
```powershell
docker exec -it ollama ollama run hermes3

```
 * **What this does for your manual:** It tells the Docker container to fetch the model from the official library.
 * **What to expect:** You will see a progress bar as it downloads (it's a few gigabytes). Once finished, it will drop you into a chat prompt inside the container.
Run that command  

![](https://i.imgur.com/FpIFzbW.png)

`/bye` to exit the chat and return to your main PowerShell command line.

Now, for **Step 12**, we will set up the **Open WebUI**. This is the dashboard interface that will allow you to interact with your Docker-based Hermes 3 model through a clean, graphical web page.

## Step 12: Install and Run Open WebUI

Run this command in your PowerShell to start the interface:

PowerShell

```
docker run -d -p 3000:8080 --add-host=host.docker.internal:host-gateway -v open-webui:/app/backend/data --name open-webui --restart unless-stopped ghcr.io/open-webui/open-webui:main
```

- **What this does:** It pulls the Open WebUI image and starts it on port `3000`.
    
- **Access:** Once it's running, you will be able to access your new AI dashboard by opening your web browser to `http://localhost:3000`.

## Step 13

### **Step 1 
Open your **n8n** web interface, navigate to the **Credentials** section, and click **Add Credential** to search for **Ollama**.

### Step 2 
![](https://i.imgur.com/NyDjimH.png)

On the left hand, top corner of the screen, hover over the plus button and Click Credentials  

### Step 3
 type **Ollama** to find the Ollama API credential, select it  


![](https://i.imgur.com/4B3DcwY.png)

Click on the orange box that says **Ollama** inside the search window to open the configuration settings  


![](https://i.imgur.com/7XXvaPy.png)

### Step 4

```text
http://host.docker.internal:11434

```
In the **Base URL** field, replace the current URL with the code block above, click the orange **Save** button in the top right corner

![517](https://i.imgur.com/jzeVCJo.png)

![](https://i.imgur.com/ObQadpR.png)
### Step 5

Excellent, your Ollama credential is saved and the connection test was successful.
**Next step:** Navigate back to your **Workflows** page (click the "Workflows" icon on the left sidebar), click the **"+ Add workflow"** button to create a new one
![](https://i.imgur.com/RGDR4kH.png)
![](https://i.imgur.com/CWqrMOB.png)
### Step 6
 Click the small **"+"** sign on the right side of the **Manual Trigger** node you just added, type **"Ollama"** in the search bar  Select the **"Ollama Chat Model"** option (the third one down in your list).
 









































































































































