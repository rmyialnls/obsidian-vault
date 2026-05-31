---
color: var(--mk-color-brown)
sticker: emoji//1f4bb
---
Perfect. "No such container" means the lane is completely clear and there are no old files blocking us.

### Step 2: Install and Start Node-RED

Paste this command into PowerShell and press **Enter**:

PowerShell

```
docker run -d -p 1880:1880 --name mynodered --restart always nodered/node-red
```


![](https://i.imgur.com/yPIKZVC.png)


### Step 3: Open Node-RED

1. Open your web browser (like Chrome or Edge).
    
2. Go to this exact address: **`http://localhost:1880`**
    

Plaintext

```
http://localhost:1880
```

![](https://i.imgur.com/el9pCyE.png)


### Step 4 Ngrok 


Copy this code block, paste it into your open PowerShell window, and press **Enter**:

PowerShell

```
winget install ngrok.ngrok
```

Let me know what text or progress bar appears on your screen once you run it.

![](https://i.imgur.com/QznddTc.png)



The latest screenshot shows that ngrok has **successfully installed** and the command line alias "ngrok" has been added.

This is the perfect screen to snapshot for your Obsidian setup notes to prove the fresh installation is complete.

### Step 2: Connect your ngrok account

To actually use ngrok, you need to add your personal security token so your computer is authorized to create tunnels.

Log into your online ngrok dashboard, copy your unique authtoken command, paste it into PowerShell, and hit **Enter**. It will look exactly like this snippet layout:

PowerShell

```
ngrok config add-authtoken YOUR_ACTUAL_TOKEN_HERE
```

Run that command, and let me know when it says the authtoken has been saved to your configuration file.

### Step 5

PowerShell

```
ngrok config add-authtoken 3DxPwL3TA9un2o2MAcXNhpSXHx8_aX7WoL86s6VdSdKHwKhK
```

![](https://i.imgur.com/K0ol86L.png)


ngrok authtoken  : 3DxPwL3TA9un2o2MAcXNhpSXHx8_aX7WoL86s6VdSdKHwKhK

The latest screenshot looks excellent. It shows that your authtoken was successfully saved to your configuration file.

This is the exact proof shot to drop into your Obsidian vault for your ngrok setup documentation.

### Step 3: Test your tunnel

Now we do a quick test to ensure ngrok can open a communication lane cleanly. Since your Node-RED is currently listening on port 1880, we will tell ngrok to point directly at it.

Copy this command, paste it into your PowerShell window, and hit **Enter**:

PowerShell

```
ngrok http 1880
```

Once you run it, your PowerShell window will change to an active status screen showing your new public link. Let me know when that status screen appears!

The new screenshot shows that `ngrok update` worked perfectly. It completely bypassed the winget restriction and updated you directly to version 3.39.5.

Your account is now fully authorized and running on the modern version required by your plan. This is the exact shot you want to grab for your Obsidian notes showing a successful update.

Now we can officially fire up the tunnel test.

Copy this code snippet, paste it into your PowerShell window, and hit **Enter**:

PowerShell

```
ngrok http 1880
```

Let me know as soon as the status grid layout fills your PowerShell window.



![](https://i.imgur.com/YQ5AGe5.png)
The new screenshot shows your tunnel is officially **online and active**.

Look at the **Forwarding** line on your screen:

`https://catty-cone-deferred.ngrok-free.dev -> http://localhost:1880`

This means your secure public tunnel is perfectly built. Any outside application (like Gmail) can now send data to that `ngrok-free.dev` web address, and it will drop straight onto your local computer.

Take a screenshot of this active status window for your Obsidian notes. This is your definitive "Step 3: ngrok Tunnel Live" proof.

Whenever you want to close this test tunnel and get your regular command line back, just press **Ctrl + C**.
https://catty-cone-deferred.ngrok-free.dev

### Step 4

Do not close that PowerShell window. If you close it, your tunnel dies instantly, and the link will stop working in Chrome.

You do not need to put ngrok in a Docker container right now. Leaving it running directly in that open PowerShell window is perfectly fine for your setup.

To make ngrok run permanently in the background without keeping a manual PowerShell window open, we will use a Windows Service. This is much simpler than Docker and survives a computer reboot.

First, close your active tunnel in the current PowerShell window by pressing **Ctrl + C**.

Once your regular prompt `PS C:\Users\rmyia>` is back, copy this code snippet to install and start ngrok as a background service, paste it into PowerShell, and press **Enter**:

PowerShell

```
ngrok service install --config C:\Users\rmyia\AppData\Local\ngrok\ngrok.yml
ngrok service start
```

### Step 5

I will stick to one step at a time from now on.

When you are ready in the new Administrator PowerShell window, paste this command and hit **Enter** to install the background service:

PowerShell

```
ngrok service install --config C:\Users\rmyia\AppData\Local\ngrok\ngrok.yml
```
Start-Process powershell -Verb runAs

The new screenshot shows you accidentally pasted the backticks (```) along with the command, which triggered another error.

Let's do it cleanly with no formatting backticks. Copy only the text inside the box below, paste it into your current PowerShell window, and hit **Enter**:

PowerShell

Start-Process powershell -Verb runAs


Now that your new Administrator PowerShell window is open, copy this single command, paste it in, and press **Enter** to install the background service:

PowerShell

```
ngrok service install --config C:\Users\rmyia\AppData\Local\ngrok\ngrok.yml
```

<!--⚠️Imgur upload failed, check dev console-->
![](https://i.imgur.com/n7Dgl48.png)

### step 6
Perfect, it says `install ok`. The service is officially installed.

Now, copy this next command, paste it in, and press **Enter** to start it up:

PowerShell

```
ngrok service start
```


![](https://i.imgur.com/tzuws5x.png)


Ah, I see what happened. You ran `ngrok service start` in your original, normal PowerShell window instead of the blue Administrator one, so it hit another "Access is denied" error.

Because you closed the blue window, we need to open it up one more time just to start the service.

Copy this command, paste it into your current black window, and hit **Enter**:

PowerShell

```
Start-Process powershell -Verb runAs
```

Now that your blue Administrator PowerShell window is open again, copy this single command, paste it in, and press **Enter** to start the background service:
```powershell
ngrok service start

```

The screenshot shows start ok, meaning ngrok is now running automatically in the background. You can safely close all open PowerShell windows.
To find your new web link, open Google Chrome, type this address into the search bar at the top, and hit **Enter**:
```text
http://127.0.0.1:4040/status

```

![](https://i.imgur.com/iNf6mdv.png)

The background service is online, but your active tunnel config isn't showing up on this dashboard yet because the configuration file needs the specific tunnel details.

Let's fix that. Click on the **Node-RED : Flow 1** tab at the top of your Chrome window, copy the URL web address from that tab's address bar, and paste it here so I can see the exact port number it is using.


Perfect, Node-RED is running on port `1880`.

Because you want ngrok to run fully by itself in the background, we need to add a default tunnel pointing to that port inside your main configuration file.

Let's open the config file to edit it. Open your normal PowerShell window, copy this single command, paste it in, and press **Enter**:

PowerShell

```
notepad C:\Users\rmyia\AppData\Local\ngrok\ngrok.yml
```

Got it. If you already have one of the PowerShell windows open, click into it, copy this command, paste it in, and press **Enter**:

PowerShell

```
notepad C:\Users\rmyia\AppData\Local\ngrok\ngrok.yml
```


Perfect, Notepad is open with your configuration file.

Copy the exact block of text below, paste it into Notepad on a new line right below your authtoken, and save the file (Ctrl + S):

YAML

```
tunnels:
  nodered:
    proto: http
    addr: 1880
```


![](https://i.imgur.com/arHI8O7.png)

The file formatting looks perfect.

Press **Ctrl + S** inside Notepad to save the changes, then close Notepad.

Since we updated the configuration file, we need to restart the background service for it to take effect. Open your normal PowerShell window, copy this command, paste it in, and press **Enter**:

PowerShell

```
Start-Process powershell -Verb runAs
```


The commands got mixed together on the same line in your window, which caused that error.

Let's clear that line. Copy only the single clean command below, paste it into your black PowerShell window, and hit **Enter**:

PowerShell

```
Start-Process powershell -Verb runAs
```


The blue Administrator window is open now.
Copy this single command, paste it into that blue window, and hit **Enter** to restart the service with the new configuration:
```powershell
ngrok service restart

```

![](https://i.imgur.com/OVfopzL.png)



The service restarted successfully (restart ok). You can now close all open PowerShell windows.
Go back to Google Chrome, open your **ngrok - Status** tab (or refresh http://127.0.0.1:4040/status), and you will see your new live public web address listed under the tunnels section.

```text
http://127.0.0.1:4040/status

```

The tunnel configuration worked perfectly. You can see nodered listed on the right side of your dashboard now under connections and requests.
To find your live public web address, click on the **Inspect** tab at the top of that webpage (right next to the green *online* dot). It will show you the exact public https:// link pointing to your Node-RED setup.


My apologies, I misread the blurry text in the previous screenshot.
Here is your exact public URL copied directly from your screen:
```text
https://catty-cone-deferred.ngrok-free.dev

```


