<body>
<p align="center">
    <img src="https://i.imgur.com/mhM2vuW.png" alt="NotTokens" width="300">
</p>
<p align="center">
    <a href="https://github.com/No-Not-Jaden/NotTokensPremium/wiki"><img src="https://i.imgur.com/EyySsSx.png"
                                                                         alt="Wiki" width="175"></a>
    <a>&nbsp;&nbsp;&nbsp;&nbsp;</a>
    <a href="https://discord.gg/zEsUzwYEx7"><img src="https://i.imgur.com/KQY7rx1.png" alt="Discord" width="200"></a>
    <a>&nbsp;&nbsp;&nbsp;&nbsp;</a>
    <a href="https://github.com/No-Not-Jaden/NotTokensPremium/releases"><img src="https://i.imgur.com/2e3iQ0L.png"
                                                                             alt="Proxy" width="175"></a>
</p>
<p align="center">
    <img src="https://i.imgur.com/c7X3Xz5.png" alt="About">
</p>
<p align="center">
    <a>Add another layer to your economy with NotTokens. Easily give tokens values with built-in item exchange commands.
        Use on a single server, or connect multiple servers with a <a href="https://github.com/No-Not-Jaden/NotTokensPremium/releases">proxy</a> or MySQL. Supports all versions
        from 1.8-1.20.4. This redesigns my old NotTokens plugin which only had a few features. </a>
</p>
<p align="center">
    <img src="https://i.imgur.com/1MWv5Vz.png" alt="Features">
</p>
<ul>
    <li>Velocity & Bungeecord proxy support</li>
    <li>MySQL Compatibility</li>
    <li>1.8-1.20.4 Support</li>
    <li>Exchange Items for Tokens</li>
    <li>Token Rewards for Killing Entities</li>
    <li>PlaceholderAPI Placeholders</li>
    <li>Skript Compatibility</li>
    <li>Spam Reducing Features</li>
    <li>Offline Player Support</li>
    <li>Customizable Messages</li>
    <li>Transaction logs</li>
    <li>Migrate Tokens from Another Plugin</li>
</ul>
<p align="center">
    <img src="https://i.imgur.com/PYSzcCf.png" alt="Commands">
</p>
<ul>
    <li>/token - View your current token balance.</li>
    <li>/token transfer (player) (amount) - Transfer your tokens to another player.</li>
    <li>/token deposit (amount/all) - Deposit the item exchange object into your account for the exchange value of
        tokens.
    </li>
    <li>/token withdraw (amount/all) - Withdraw the item exchange object from your account for its exchange value of
        tokens.
    </li>
    <li>/token top - View the players with the most tokens.</li>
    <li>/token (player) - View a player's current token balance.</li>
    <li>/token give (player) (amount) - Give a player tokens.</li>
    <li>/token remove (player) (amount) - Remove a player's tokens.</li>
    <li>/token set (player) (amount) - Set a player's tokens.</li>
    <li>/token giveall (amount) &#60;online/offline>- Give tokens to all offline or online players.</li>
    <li>/token removeall (amount) &#60;online/offline>- Remove tokens from all offline or online players.</li>
    <li>/token migrate (plugin name) (SWAP/ADD/TRANSFER/COPY/REPLACE/NONE) - Migrates another plugin's tokens into
        NotTokens.
    </li>
    <li>/token reload - Reloads this plugin.</li>
</ul>
<p align="center">
    <img src="https://i.imgur.com/5lx4Qzu.png" alt="Permissions">
</p>

<pre>
nottokens.admin          -   Reload the plugin or migrate another plugin’s tokens
↳ nottokens.edit         -   Edit players’ tokens
↳ nottokens.viewother    -   View other players’ tokens
↳ nottokens.player       -   Player commands
  ↳ nottokens.top        -   View the top tokens leaderboard
  ↳ nottokens.transfer   -   Transfer your tokens to another player
  ↳ nottokens.balance    -   Check your token balance
  ↳ nottokens.exchange   -   Exchange items for tokens and vice versa
</pre>
<p align="center">
    <img src="https://i.imgur.com/cJk6c08.png" alt="Dependencies">
</p>
<p><a>All dependencies here are optional addons.</a>
<h1>PlaceholderAPI</h1>
<a>For the use in item exchange and NotTokens's placeholders shown below.</a>
<ul>
    <li>%nottokens_amount% - Token amount.</li>
    <li>%nottokens_amount_formatted% - Formatted token amount.</li>
    <li>%nottokens_prefix% - Currency prefix.</li>
    <li>%nottokens_suffix% - Currency suffix.</li>
    <li>%nottokens_top_&#60;x>% - Leaderboard text for the player at rank &#60;x>.</li>
</ul>
<h1>Skript</h1>
<a>For using Skripts that modify or read token values.</a>
<br>Example Script</br>
<pre>
command /skripttokens:
   trigger:
       send the tokens of player
command /settokens &#60;player> &#60;number>:
   trigger:
       set tokens of arg-1 to arg-2
command /removetokens &#60;player> &#60;number>:
   trigger:
       remove arg-2 from tokens of arg-1
    </pre>
<h1>Migratable Plugins</h1>
<a>The tokens from these plugins can be migrated using the in-game-command: <code>/nottokens migrate (plugin name)
    (SWAP/ADD/TRANSFER/COPY/NONE)</code></a>
<ul>
    <li>TokenManager</li>
    <li>BeastTokens</li>
</ul>
</p>

</body>
