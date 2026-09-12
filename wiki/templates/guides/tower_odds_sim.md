Below is a JavaScript utility which can simulate attempting a tower floor with a given loadout.

The utility was coded with contributions from Gemma-4-12B-it, Gemma-4-E4B-it and gpt-oss-20b.

The combat mechanics for this utility have been rewritten in JS based on the original game files.

An attempt has been made to ensure that the JS is as close as possible to the original game mechanics,
but some inaccuracy is possible.

## Table of Contents
- [Purpose](#purpose)
- [Instructions](#instructions)
- [Simulator](#simulator)

## Purpose

The purpose of this utility is to enable the player to test different gear setups in the tower and determine whether they are likely to beat a stage or what stats and food
they will need to do so.
For more information on how the tower works and general strategies for it, please check out the <a href=./guide_the_infinite_tower.html>Infinite Tower Guide</a> by Michael.

## Instructions

The fields are to be filled out as follows:

- **Tower Floor**     - The tower floor you wish to simulate.
- **Runs**            - The amount of simulated attempts.
- **Attack**          - The player base attack + potion attack bonus + prestige bonuses + bonus from cape multiplier. Gear bonuses (including that from the equipped cape) are **not** to be included here.
- **Strength**        - The player base strength level + potion strength bonus + prestige bonuses + bonus from cape multiplier. Gear bonuses (including that from the equipped cape) are **not** to be included here.
- **Defense**         - The total defense level available, including base level, gear bonuses, potion, prestige, cape multiplier, and blessing.
- **HP Level**        - The character HP level. Note that this is skill level HP, so if it is set to 20, the actual HP in the fight will be 200.
- **Gear Atk Bonus**  - The bonus attack received from all equipped gear (this can be seen in the combat skill screen as +xx gear).
- **Gear Str Bonus**  - The bonus strength received from all equipped gear (this can be seen in the combat skill screen as +xx gear).
- **Atk Speed**       - This is the player attack speed, and can depend on the weapon. Ususally 2.4.
- **Eat Threshold %** - The HP threshold at which the player will start eating (game setting at the bottom of the combat gear screen, default 50%).
- **Double Hit**      - If the double hit ability is available, it can be set here between 0 and 1 (0 = 0%, 1 = 100%). If this ability is not available, just leave the value at 0.
- **Combat Style**    - The combat style loadout. Currently only attack and strength are available in this simulator.
- **Second Chance**   - If the second chance ability is available, it can be enabled here.
- **Food**            - Here the equipped food can be specified in a comma separated list as food:qty, food:qty (e.g. manta_ray:200, beef:20).


**Available food types are given below:**

  rat_meat, chicken, mutton, beef, shrimp, sardine, herring, mackerel, trout, salmon, tuna, lobster, swordfish, monkfish, shark, sea_turtle, manta_ray
  
  Food will be consumed in the order of highest heal value first up to a maximum consumption of 300 food items as per the game combat mechanics (there is a game setting to alter
  the order of food consumption, but that is generally undesirable for a difficult level and has not been implemented in this utility).
  
## Simulator

<!-- Tower Simulator Component Start -->
<style>
    .tower-sim-wrapper {
        background: #1e1e1e;
        color: #e0e0e0;
        padding: 25px;
        border-radius: 12px;
        border: 1px solid #444;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        max-width: 850px;
        margin: 20px auto;
    }
    .tower-sim-wrapper h2 { color: #4CAF50; margin-top: 0; }
    .sim-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 15px;
        margin-bottom: 20px;
    }
    @media (max-width: 450px) {
        .sim-grid {
            /* Forces a single column layout when the screen is 450px or less */
            grid-template-columns: 1fr; 
            gap: 10px; /* Slightly reduce gap for mobile */
        }
    
        /* Ensure the wide food input takes full width even when in a single column */
        .sim-input-group[style*="span 2"] {
            grid-column: auto !important; 
        }
    }
    .sim-input-group { display: flex; flex-direction: column; }
    .sim-input-group label {
        font-size: 0.75rem;
        color: #888;
        margin-bottom: 4px;
        text-transform: uppercase;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }
    .sim-input-group input, .sim-input-group select {
        background: #333;
        border: 1px solid #555;
        color: white;
        padding: 8px;
        border-radius: 4px;
    }
    .sim-btn {
        background: #4CAF50;
        color: white;
        padding: 15px;
        border: none;
        border-radius: 6px;
        font-weight: bold;
        cursor: pointer;
        width: 100%;
        font-size: 1.1rem;
        transition: background 0.2s;
    }
    .sim-btn:hover { background: #45a049; }
    .sim-btn:disabled { background: #555; cursor: not-allowed; }

    #sim-reset-btn {
        background: #f44336; /* Red for reset */
    }
    #sim-reset-btn:hover { background: #d32f2f; }
    
    .sim-progress-container {
        margin-top: 20px;
        display: none;
    }
    .sim-progress-bar-bg {
        width: 100%;
        background: #444;
        height: 12px;
        border-radius: 6px;
        overflow: hidden;
    }
    .sim-progress-fill {
        width: 0%;
        height: 100%;
        background: #4CAF50;
        transition: width 0.1s;
    }
    #sim-result-display {
        margin-top: 20px;
        font-size: 1.5rem;
        text-align: center;
        font-weight: bold;
        color: #4CAF50;
    }
    #sim-error-display {
        margin-top: 15px;
        padding: 10px;
        border: 1px solid #f44336;
        background-color: #330a0a;
        color: #ff8a80;
        border-radius: 6px;
        display: none; /* Hidden by default */
        text-align: left;
    }
</style>

<div class="tower-sim-wrapper">
    <h2>Tower Simulator</h2>
    <div class="sim-grid">
        <div class="sim-input-group"><label title="Tower Floor">Tower Floor</label><input type="number" id="sim-floor" value="1"></div>
        <div class="sim-input-group"><label title="Runs (Number of runs that will be simulated)">Runs</label><input type="number" id="sim-runs" value="1000"></div>
        <div class="sim-input-group"><label title="Attack (Base + Potion + Prestige + Bonus from CapeMult)">Attack (Base+Potion+Prestige)</label><input type="number" id="sim-atk" value="1"></div>
        <div class="sim-input-group"><label title="Strength (Base + Potion + Prestige + Bonus from CapeMult)">Strength (Base+Potion+Prestige)</label><input type="number" id="sim-str" value="1"></div>
        <div class="sim-input-group"><label title="Defense (Total, Incl. Base, Potion, Prestige, Blessing, Cape Multiplier Bonus)">Defense (Total)</label><input type="number" id="sim-def" value="1"></div>
        <div class="sim-input-group"><label title="HP Level">HP Level</label><input type="number" id="sim-hp" value="1"></div>
        <div class="sim-input-group"><label title="Gear Attack Bonus">Gear Atk Bonus</label><input type="number" id="sim-w-atk" value="0"></div>
        <div class="sim-input-group"><label title="Gear Strength Bonus">Gear Str Bonus</label><input type="number" id="sim-w-str" value="0"></div>
        <div class="sim-input-group"><label title="Attack Speed (generally should be left at the default of 2.4)">Atk Speed (sec)</label><input type="number" step="0.1" id="sim-atk-speed" value="2.4"></div>
        <div class="sim-input-group"><label title="Eat Threshold">Eat Threshold %</label><input type="number" id="sim-eat-thresh" value="50"></div>
        <div class="sim-input-group"><label title="Double Hit (0.0-1.0)">Double Hit (0.0-1.0)</label><input type="number" step="0.1" id="sim-double-hit" value="0.0"></div>
        <div class="sim-input-group">
            <label title="Combat Style">Combat Style</label>
            <select id="sim-style">
                <option value="attack">Attack</option>
                <option value="strength">Strength</option>
            </select>
        </div>
        <div class="sim-input-group">
            <label title="Second Chance">Second Chance</label>
            <select id="sim-sec-chance">
                <option value="false">False</option>
                <option value="true">True</option>
            </select>
        </div>
        <div class="sim-input-group" style="grid-column: span 2;">
            <label title="Food (type:qty, comma separated)">Food (type:qty, comma separated)</label>
            <input type="text" id="sim-food-input" value="beef:50, shark:20" placeholder="manta_ray:300, beef:50, shark:20">
        </div>
    </div>

    <div style="display: flex; gap: 10px; margin-bottom: 20px;">
        <button id="sim-run-btn" class="sim-btn" style="flex-grow: 1;">Run Simulation</button>
        <button id="sim-reset-btn" class="sim-btn">Reset Inputs</button>
    </div>
    <div id="sim-error-display"></div>

    <div class="sim-progress-container" id="sim-progress-box">
        <div class="sim-progress-bar-bg"><div id="sim-progress-fill" class="sim-progress-fill"></div></div>
        <div id="sim-progress-text" style="text-align:center; margin-top:5px;">0%</div>
    </div>

    <div id="sim-result-display"></div>
</div>

<script>
(function() {
    const FOOD_MAP = {FOODMAP};

    const ENEMIES = {ENEMIES};

    const FLOOR_TIERS = [
        { range: [1, 20], spawns: [{ enemy: "goblin", weight: 40 }, { enemy: "skeleton", weight: 30 }, { enemy: "zombie", weight: 30 }] },
        { range: [21, 40], spawns: [{ enemy: "orc_warrior", weight: 40 }, { enemy: "dark_wizard", weight: 30 }, { enemy: "bandit", weight: 30 }] },
        { range: [41, 60], spawns: [{ enemy: "cave_troll", weight: 35 }, { enemy: "shadow_beast", weight: 35 }, { enemy: "demon", weight: 30 }] },
        { range: [61, 80], spawns: [{ enemy: "forge_demon", weight: 35 }, { enemy: "shadow_assassin", weight: 35 }, { enemy: "abyssal_leech", weight: 30 }] },
        { range: [81, 100], spawns: [{ enemy: "void_stalker", weight: 35 }, { enemy: "void_guardian", weight: 35 }, { enemy: "abyssal_lord", weight: 30 }] },
        { range: [101, 999999], spawns: [{ enemy: "void_archon", weight: 35 }, { enemy: "eternal_sentinel", weight: 35 }, { enemy: "abyssal_lord", weight: 30 }] }
    ];

    function getTierFor(floor) {
        return FLOOR_TIERS.find(t => floor >= t.range[0] && floor <= t.range[1])?.spawns || FLOOR_TIERS[FLOOR_TIERS.length - 1].spawns;
    }

    function getScaledEnemies(floor, enemies) {
        if (floor <= 100) return enemies;
        let hpMult = 1.0 + ((Math.min(Math.max(floor, 101), 250) - 100) / 150.0) * 9.0;
        let statMult = 1.0 + ((Math.min(Math.max(floor, 101), 250) - 100) / 150.0) * 0.3;
        
        let relevant = getTierFor(floor).map(s => s.enemy);
        let res = {};
        for (let key in enemies) {
            let enemy = { ...enemies[key], combat: { ...enemies[key].combat }, defensive: { ...enemies[key].defensive } };
            if (relevant.includes(key)) {
                enemy.hp = Math.max(1, Math.floor(enemy.hp * hpMult));
                enemy.combat.attackBonus = Math.floor(enemy.combat.attackBonus * statMult);
                enemy.combat.strengthBonus = Math.floor(enemy.combat.strengthBonus * statMult);
                enemy.defensive.attackDefense = Math.floor(enemy.defensive.attackDefense * statMult);
                enemy.defensive.strengthDefense = Math.floor(enemy.defensive.strengthDefense * statMult);
                enemy.defensive.rangedDefense = Math.floor(enemy.defensive.rangedDefense * statMult);
                enemy.defensive.magicDefense = Math.floor(enemy.defensive.magicDefense * statMult);
            }
            res[key] = enemy;
        }
        return res;
    }

    function simulateFloor(floor, enemies, foods, load) {
        const enemiesScaled = getScaledEnemies(floor, enemies);
        let maxHp = load.hpLevel * 10;
        let currentHp = maxHp;

        let foodSupply = {};
        for (let type in load.foodSupply) foodSupply[type] = load.foodSupply[type];
        
        let foodOrder = Object.keys(foodSupply).filter(k => FOOD_MAP[k]);
        foodOrder.sort((a, b) => FOOD_MAP[b].heal - FOOD_MAP[a].heal);

        let totalFoodEaten = 0;
        let spawnPool = [];
        getTierFor(floor).forEach(s => {
            for (let i = 0; i < s.weight; i++) spawnPool.push(s.enemy);
        });

        if (spawnPool.length === 0) return true;

        let attackSpeedSec = Math.max(1.2, load.attackSpeedSec);
        let ticksPerFrame = Math.floor(60.0 / attackSpeedSec + 0.5);
        let timePerTick = 60.0 / ticksPerFrame;

        let carryoverEnemyKey = "";
        let carryoverHp = 0;

        for (let minute = 1; minute <= 60; minute++) {
            let enemyKey;
            if (carryoverEnemyKey !== "") {
                enemyKey = carryoverEnemyKey;
                carryoverEnemyKey = "";
            } else {
                enemyKey = spawnPool[Math.floor(Math.random() * spawnPool.length)];
            }

            const enemy = enemiesScaled[enemyKey];
            let enemyHp = carryoverHp > 0 ? carryoverHp : enemy.hp;

            let playerEffAtk = load.attack + load.weaponAttackBonus + (load.potionBonus["attack"] || 0);
            let playerEffDef = load.defense + (load.potionBonus["defense"] || 0);
            let enemyDefStat = (load.combatStyle === "strength") ? enemy.defensive.strengthDefense : enemy.defensive.attackDefense;

            let playerHitChance = playerEffAtk > enemyDefStat ? 1.0 - enemyDefStat / (2.0 * Math.max(1, playerEffAtk)) : playerEffAtk / (2.0 * Math.max(1, enemyDefStat));
            playerHitChance = Math.min(0.95, Math.max(0.15, playerHitChance));

            let playerEffStr = load.strength + load.weaponStrengthBonus + (load.potionBonus["strength"] || 0);
            let playerMaxHit = Math.max(1, Math.floor(1 + (playerEffStr * (load.weaponStrengthBonus + 64)) / 640));

            let enemyEffStr = enemy.combat.strengthLevel + enemy.combat.strengthBonus;
            let enemyMaxHit = enemyEffStr === 0 ? 0 : Math.max(0, Math.floor(1 + enemyEffStr * (enemy.combat.strengthBonus + 64) / 640));

            let enemyEffAtk = enemy.combat.attackLevel + enemy.combat.attackBonus;
            let enemyHitChance = enemyEffAtk > playerEffDef ? 1.0 - playerEffDef / (2.0 * Math.max(1, enemyEffAtk)) : enemyEffAtk / (2.0 * Math.max(1, playerEffDef));
            enemyHitChance = Math.min(0.95, Math.max(0.10, enemyHitChance));

            let enemyClock = 0;
            for (let tick = 0; tick < ticksPerFrame; tick++) {
                let dmg = 0;
                let r1 = Math.random();
                
                if (r1 < playerHitChance) {
                    dmg = Math.floor(Math.random() * (playerMaxHit + 1));
                } else if (load.secondChance) {
                    if (Math.random() < playerHitChance) {
                        dmg = Math.floor(Math.random() * (playerMaxHit + 1));
                    }
                }

                if (load.doubleHitChance > 0 && (enemyHp - dmg > 0)) {
                    if (Math.random() < load.doubleHitChance && Math.random() < playerHitChance) {
                        dmg += Math.floor(Math.random() * (playerMaxHit + 1));
                    }
                }

                enemyHp -= dmg;
                if (enemyHp <= 0) {
                    enemyKey = spawnPool[Math.floor(Math.random() * spawnPool.length)];
                    enemyHp = enemiesScaled[enemyKey].hp;
                    // Recalculate stats for new enemy
                    playerEffAtk = load.attack + load.weaponAttackBonus + (load.potionBonus["attack"] || 0);
                    playerEffDef = load.defense + (load.potionBonus["defense"] || 0);
                    enemyDefStat = (load.combatStyle === "strength") ? enemiesScaled[enemyKey].defensive.strengthDefense : enemiesScaled[enemyKey].defensive.attackDefense;
                    playerHitChance = playerEffAtk > enemyDefStat ? 1.0 - enemyDefStat / (2.0 * Math.max(1, playerEffAtk)) : playerEffAtk / (2.0 * Math.max(1, enemyDefStat));
                    playerHitChance = Math.min(0.95, Math.max(0.15, playerHitChance));
                    let enemyEffAtk = enemiesScaled[enemyKey].combat.attackLevel + enemiesScaled[enemyKey].combat.attackBonus;
                    enemyHitChance = enemyEffAtk > playerEffDef ? 1.0 - playerEffDef / (2.0 * Math.max(1, enemyEffAtk)) : enemyEffAtk / (2.0 * Math.max(1, playerEffDef));
                    enemyHitChance = Math.min(0.95, Math.max(0.10, enemyHitChance));
                }

                enemyClock += timePerTick;
                while (enemyClock >= 2.4 - 0.000001) {
                    enemyClock -= 2.4;
                    if (Math.random() < enemyHitChance) {
                        currentHp -= Math.floor(Math.random() * (enemyMaxHit + 1));
                    }
                }

                if (currentHp <= 0) break;

                if (foodOrder.length > 0) {
                    while (totalFoodEaten < 300) {
                        let ateThisTime = false;
                        for (let fKey of foodOrder) {
                            if (foodSupply[fKey] > 0) {
                                let heal = FOOD_MAP[fKey].heal;
                                if (currentHp <= (maxHp * load.eatThresholdPct / 100.0) || currentHp <= enemyMaxHit) {
                                    currentHp = Math.min(maxHp, currentHp + heal);
                                    foodSupply[fKey]--;
                                    totalFoodEaten++;
                                    ateThisTime = true;
                                    break;
                                }
                            }
                        }
                        if (!ateThisTime) break;
                    }
                }
            }

            if (enemyHp > 0 && enemyHp < enemiesScaled[enemyKey].hp) {
                carryoverHp = enemyHp;
                carryoverEnemyKey = enemyKey;
            }
            if (currentHp <= 0) return false;
        }
        return currentHp > 0;
    }

    // --- NEW RESET LOGIC ---
    const btnRun = document.getElementById('sim-run-btn');
    const btnReset = document.getElementById('sim-reset-btn'); // Reference to the new button
    
    function resetInputs() {
        // Set inputs and selects to default values
        document.getElementById('sim-floor').value = 1;
        document.getElementById('sim-runs').value = 1000;
        document.getElementById('sim-atk').value = 1;
        document.getElementById('sim-str').value = 1;
        document.getElementById('sim-def').value = 1;
        document.getElementById('sim-hp').value = 1;
        document.getElementById('sim-w-atk').value = 0;
        document.getElementById('sim-w-str').value = 0;
        document.getElementById('sim-atk-speed').value = 2.4;
        document.getElementById('sim-eat-thresh').value = 50;
        document.getElementById('sim-double-hit').value = 0.0;
        
        // Reset select elements
        document.getElementById('sim-style').value = "attack"; // Default: attack
        document.getElementById('sim-sec-chance').value = "false"; // Default: false

        // Reset food input
        document.getElementById('sim-food-input').value = "beef:50, shark:20";
    }
    // -----------------------

    const progBox = document.getElementById('sim-progress-box');
    const progFill = document.getElementById('sim-progress-fill');
    const progText = document.getElementById('sim-progress-text');
    const resultDisp = document.getElementById('sim-result-display');
    const errorDisp = document.getElementById('sim-error-display'); // Error display element

    /**
     * Performs comprehensive validation on all simulation inputs.
     * @returns {string|null} Returns error message string if invalid, or null if valid.
     */
    function validateInputs() {
        // --- 1. General Numeric Input Checks (Floor, Runs, Stats, Bonuses) ---
        
        const inputs = {
            floor: parseInt(document.getElementById('sim-floor').value),
            runs: parseInt(document.getElementById('sim-runs').value),
            atk: parseInt(document.getElementById('sim-atk').value),
            str: parseInt(document.getElementById('sim-str').value),
            def: parseInt(document.getElementById('sim-def').value),
            hp: parseInt(document.getElementById('sim-hp').value),
            w_atk: parseInt(document.getElementById('sim-w-atk').value),
            w_str: parseInt(document.getElementById('sim-w-str').value),
            eat_thresh: parseInt(document.getElementById('sim-eat-thresh').value),
            double_hit: parseFloat(document.getElementById('sim-double-hit').value),
            atk_speed: parseFloat(document.getElementById('sim-atk-speed').value)
        };

        // Check for NaN (non-numeric input)
        for (const key in inputs) {
            if (isNaN(inputs[key])) {
                return `Validation Error: All inputs must be valid numbers. The field '${key}' contains invalid data.`;
            }
        }

        // Check for mandatory positive/non-zero values
        if (inputs.floor < 1) return "Validation Error: Tower Floor must be at least 1.";
        if (inputs.runs < 1) return "Validation Error: Runs must be at least 1.";
        if (inputs.hp < 1) return "Validation Error: HP Level must be at least 1.";
        if (inputs.atk_speed < 0.1) return "Validation Error: Attack Speed must be a positive value.";
        
        // Check range constraints
        if (inputs.eat_thresh < 0 || inputs.eat_thresh > 100) return "Validation Error: Eat Threshold must be between 0 and 100.";
        if (inputs.double_hit < 0.0 || inputs.double_hit > 1.0) return "Validation Error: Double Hit chance must be between 0.0 and 1.0.";

        // --- 2. Food Input Validation ---
        const foodStr = document.getElementById('sim-food-input').value;
        const foodSupplyMap = {};
        
        if (foodStr.trim() !== "") {
            const foodItems = foodStr.split(',');
            for (const item of foodItems) {
                const parts = item.trim().split(':');
                if (parts.length !== 2) {
                    return `Validation Error: Invalid food entry format in 'Food' input. Expected format is 'type:qty', but found: '${item.trim()}'.`;
                }
                const [type, qtyStr] = parts;
                const qty = parseInt(qtyStr);

                if (!FOOD_MAP[type.trim()]) {
                    return `Validation Error: Invalid food type '${type.trim()}' specified. Please check the available types.`;
                }
                if (isNaN(qty) || qty < 1) {
                    return `Validation Error: Quantity for '${type.trim()}' must be a positive whole number.`;
                }
                foodSupplyMap[type.trim()] = qty;
            }
        }

        // All checks passed
        return null; 
    }


    btnRun.addEventListener('click', () => {
        // 1. Clear previous errors and results
        errorDisp.style.display = 'none';
        errorDisp.innerText = "";
        resultDisp.innerText = "";
        
        // 2. Run Validation
        const validationError = validateInputs();
        if (validationError) {
            errorDisp.innerText = validationError;
            errorDisp.style.display = 'block';
            btnRun.disabled = false; // Ensure button is re-enabled
            return;
        }

        // 3. Collect Load Data (only after validation)
        const floor = parseInt(document.getElementById('sim-floor').value);
        const runs = parseInt(document.getElementById('sim-runs').value);
        const foodStr = document.getElementById('sim-food-input').value;
        
        // Recalculate food supply map based on validated input
        const foodSupplyMap = {};
        foodStr.split(',').forEach(item => {
            let [type, qty] = item.split(':');
            if (type && qty) foodSupplyMap[type.trim()] = parseInt(qty.trim());
        });

        const load = {
            attack: parseInt(document.getElementById('sim-atk').value),
            strength: parseInt(document.getElementById('sim-str').value),
            defense: parseInt(document.getElementById('sim-def').value),
            hpLevel: parseInt(document.getElementById('sim-hp').value),
            weaponAttackBonus: parseInt(document.getElementById('sim-w-atk').value),
            weaponStrengthBonus: parseInt(document.getElementById('sim-w-str').value),
            attackSpeedSec: parseFloat(document.getElementById('sim-atk-speed').value),
            eatThresholdPct: parseInt(document.getElementById('sim-eat-thresh').value),
            doubleHitChance: parseFloat(document.getElementById('sim-double-hit').value),
            secondChance: document.getElementById('sim-sec-chance').value === "true",
            combatStyle: document.getElementById('sim-style').value,
            foodSupply: foodSupplyMap,
            potionBonus: {},
        };

        btnRun.disabled = true;
        btnReset.disabled = true; // Disable reset while running
        progBox.style.display = 'block';
        resultDisp.innerText = "";
        
        let successes = 0;
        
        // Use a small timeout to allow the UI to update the progress bar
        let currentRun = 0;
        const batchSize = 50; 
        
        function runBatch() {
            const end = Math.min(currentRun + batchSize, runs);
            for (; currentRun < end; currentRun++) {
                if (simulateFloor(floor, ENEMIES, FOOD_MAP, load)) {
                    successes++;
                }
            }
            
            let percent = Math.floor((currentRun / runs) * 100);
            progFill.style.width = percent + "%";
            progText.innerText = percent + "%";

            if (currentRun < runs) {
                requestAnimationFrame(runBatch);
            } else {
                btnRun.disabled = false;
                btnReset.disabled = false; // Re-enable reset
                let rate = (successes / runs) * 100;
                resultDisp.innerText = `Success rate: ${rate.toFixed(2)}% (${successes}/${runs})`;
            }
        }

        runBatch();
    });

    btnReset.addEventListener('click', () => {
        resetInputs();
        // Clear simulation state when resetting
        progBox.style.display = 'none';
        resultDisp.innerText = "";
        errorDisp.style.display = 'none';
        btnRun.disabled = false;
        btnReset.disabled = false;
    });
})();
</script>
<!-- Tower Simulator Component End -->

