![Discord](https://img.shields.io/discord/917595056075071488)
# GlobalImageBan
A mindustry plugin that checks if a image is banned. No more nsfw (hopefully)

**ATTENTION: We need people to submit NSFW Schematics in the [BMI](https://discord.gg/v7SyYd2D3y) server (Only 18+ Allowed). The more submissions we get the more NSFW get blocked.**

## Installation Guide
1. Download the latest mod verion in [#Releases](https://github.com/L0615T1C5-216AC-9437/MaxRateCalculator/releases).  
2. Go to your server's directory \ config \ mods
3. Move the mod (`Jar` file) into the mods folder  
4. Restart the server.
5. Use the `mods` command to list all mods. If you see GIB as a mod, GIB was successfully installed.
## Usage
The plugin will scan the code of logic blocks, only when placed, for `drawflush` which signifies the code prints to a screen.  
The code is then hashed and sent to `http://c-n.ddns.net:9999` to see if the hash is banned.  
If the hash is banned, two things can happen detepding on settings:  
1. (Default) Everyone *except* for the person placing the block (player 1) will receive a message saying player 1 is placing NSFW @ (X,Y)
2. Player placing the block will b e kicked for 3h automatically (See below)
<img src="readme/kick.png" alt="MaxRatio" width="500"/>  

## Settings  
`gib_complexSearch` (Boolean): If true, each drawflush will be checked individually.  
`gib_kickOnHit` (Boolean): If true, player building banned image will be kicked immediately.  
## Commands  
`gibtcs`: Toggles gib_complexSearch On/Off  
`gibtkoh`: Toggles gib_kickOnHit On/Off  
## RPC Info
Rate Limit: A maximum of 100 Requests every 30s
