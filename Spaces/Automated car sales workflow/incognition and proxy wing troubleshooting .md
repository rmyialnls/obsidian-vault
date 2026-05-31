---
color: var(--mk-color-brown)
sticker: emoji//1f4bb
---
![](https://i.imgur.com/ZCiLXeZ.png)


Based on the screenshots, your Incogniton profile edmontoncfs1 is throwing an **ERR_TUNNEL_CONNECTION_FAILED** error when you try to load a page.
This specific error means Incogniton is successfully launching the browser, but the **proxy server** assigned to that profile is failing to establish a secure tunnel connection to the internet.
Here is how to quickly fix it:
### 1. Check the Proxy Status in Incogniton
Looking at your first screenshot, the proxy speed indicator on the right shows a red lightning bolt symbol next to 1.... This usually indicates a timeout or a failed connection test.
 * In Incogniton, go to **Proxy Management** (or edit this specific profile).
 * Find the proxy credentials for edmontoncfs1 and click **Check Proxy**.
 * If it fails the validation test there, the issue is entirely with the proxy provider or the specific IP assigned to you.

Because you embedded the proxy directly into the browser profile rather than saving it to the global list, it won't show up in the main **Proxy Management** tab.
To find, test, or edit that proxy, you need to go back to the profile itself. Here is exactly where it is:
### Step 1: Go to Profile Management
 1. Click **Profile Management** on the left sidebar menu (the page from your very first screenshot).
 2. Look at the edmontoncfs1 profile row.
### Step 2: Edit the Profile to See the Proxy
 1. On the far right of the edmontoncfs1 row, click the **three vertical dots** (next to the "Start" button).
 ![](https://i.imgur.com/HiMVk6Z.png)

 2. Select **Edit** from the dropdown menu.                                                                         ![](https://i.imgur.com/tka4yE7.png)
                              
 3. In the profile settings area that opens, click on the **Proxy** tab on the left.                 ![](https://i.imgur.com/rPWBq0o.png)
 
From there, you will see the exact IP, port, username, and password fields for that proxy. You can also click the **Check proxy** button right there to see if it is alive or dead.
![](https://i.imgur.com/xgj11AW.png)

![](https://i.imgur.com/eVJye6o.png)
The issue is immediately clear from the settings page. Look at the orange text on the right side: **"Could not connect to proxy, but best guess:"** The proxy test failed because your configuration fields are mixed up. Here is exactly what is wrong and how to fix it:
### The Problem
 * **Proxy Password Field:** Your password field currently contains configuration strings like _country-ca_city-edmonton_isp-teluscommunica.
 * **Proxy Host/Port:** Your proxy field shows a generic placeholder string (premium.proxywing.com:12345).
Because it's failing to authenticate with those exact credentials, the browser cannot build a tunnel connection, resulting in the ERR_TUNNEL_CONNECTION_FAILED error you saw earlier.
### How to Fix It
 1. **Get your correct credentials:** Log into your dashboard at Proxywing.                        https://proxywing.com/                                                                                                login continue with Google                                                                                         ![](https://i.imgur.com/46EYE4O.png)
select dashboard                                                                                                                  ![](https://i.imgur.com/KQfIK21.png)
select manage                                                                                                                      ![](https://i.imgur.com/CtfX6qE.png)

2. ​**Copy the raw proxy string:** Look for a single line formatted like this: host:port:username:password
3. ​**Paste to autofill:** Clear out the current **Proxy (ip:port)** field entirely, paste that entire string directly into it, and press **Enter**. Incogniton will automatically parse it out and fill the username and password fields correctly for you.

### ​Alternatively, fill them manually:

- ​**Proxy (ip:port):** Enter the actual server address and port number provided by Proxywing (e.g., 123.456.7.8:9000 or their real premium host domain).
- ​**Proxy username:** Keep your username (ybvhtwahas).
- ​**Proxy password:** Ensure _only_ the specific alphanumeric password secret is in this field, without any trailing country/city/ISP parameters appended to it, unless your provider explicitly requires that specific string format for targeting.

​Once updated, click the purple **Check proxy** button at the bottom. If it turns green and shows a successful connection, click **Update profile** on the right, and your browser will work perfectly.   

Edmonton proxy where the **** all right what the **** at right now

149.51.37.174:12345:ybvhtwahas:b1z5c7d118_country-ca_city-edmonton_isp-teluscommunicationsinc_session-zIza7LeL_streaming-1_direct-1
