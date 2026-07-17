# Combat

_Note: The explanations on this page are valid as of v1.12.3 and could be subject to changes in future versions of the game, although we'll do our best to keep this page up-to-date._

Combat is a core mechanic of the game allowing you to fight monsters for loot and receive special items only available through combat.

Dungeons and bosses have various different bonuses and features however do simulate quite similarly.

## Combat Level

Combat level acts as an overarching level indicating your combat ability. Accessing dungeons and bosses requires having at least a minimum combat level in order to enter.

Combat level is calculated using the below formula with a minimum level of at least 1:

$`Combat\ Level = \dfrac{3\times (attackLvl + strengthLvl)}{8} + \dfrac{defenceLvl + hitpointsLvl}{4}`$

## Dungeons

Dungeons provide one of the key sources of combat in Idle Fantasy allowing you to encounter many enemies over a set period of time and receive drops specific to the enemies.

More information on specific dungeons is available in {dungeons_link}.

Explanation of how dungeons are simulated in the game is available in [Dungeon Simulation](#dungeon-simulation).

## Bosses

Unlike dungeons where you face multiple enemies, bosses allow you to face a single boss and receive special loot only available for bosses such as unique weapons and pets.

Additionally, unlike dungeons, sessions for bosses only last until either you die or you defeat the boss up to the maximum time limit which depends on the boss. Also, bosses have fixed drops which do not depend on the combat style.

More information about specific bosses is available in {bosses_link}.

Information about how bosses are simulated in the game is available in [Boss Fight Simulations](#boss-fight-simulations).

## Combat stats

The various stats for the player are often boosted by many different things such as capes, etc. The terminology below is used throughout this documentation and explains how these values are calculated.

| Stat               | Calculation                                                                                                          |
|--------------------|----------------------------------------------------------------------------------------------------------------------|
| $`playerAttack`$   | $`(attackLvl \times capeMult) + 5\times attackPrestigeLvl + potionAtkBonus`$                                         |
| $`playerStrength`$ | $`(strengthLvl \times capeMult) + 5\times strengthPrestigeLvl + potionStrBonus`$                                     |
| $`playerDefence`$  | $`(defenceLvl \times capeMult) + 5\times defencePrestigeLvl + potionDefBonus + armourDefBonuses + blessingDefBonus`$ |
| $`playerMagic`$    | $`(magicLvl \times capeMult) + 5\times magicPrestigeLvl + potionMagBonus`$                                           |
| $`playerRanged`$   | $`(rangedLvl \times capeMult) + 5\times rangedPrestigeLvl + potionRanBonus`$                                         |

## Dungeon Simulation

When you start a combat session, like all general skill simulations, the game simulates a total of 60 frames of combat. Additionally, each frame is again split into a set number of ticks where the actual combat takes place.

Each frame works by spawning an enemy type and then running a tick-by-tick combat loop.

### Enemy Spawning

During the enemy spawning phase, the enemies are spawned from a spawn pool specific to that dungeon. The spawn pool is generated from a weighted list of potential enemies and is chosen randomly.

During a single frame, only one enemy type is encountered, and if an enemy is killed during the tick-by-tick combat loop, then the same enemy is respawned repeatedly until the next frame.

If no enemies were killed in a frame then the enemy currently being attacked is carried over to the new frame where combat continues. However, if at least one kill has occurred, any progress towards killing the last enemy is lost once the frame ends.

### Tick-by-tick combat loop

The tick-by-tick combat loop runs a number of ticks in which the player and enemy take turns attacking.

The tick-by-tick combat loop goes through 4 distinct phases:
1. Player attack
2. Enemy respawning/rewards
3. Enemy attack
4. Healing the player

#### Tick speed

The enemy always attacks every "2.4s" or in other words will make $`\frac{60}{2.4} = 25\ hits`$ per frame.

The player on the other hand has their attack speed moderated by the weapon they're holding with the fastest possible speed being 1.2s (50 hits) and the slowest being 2.4s (25 hits).

#### Phase 1: Player attack

During this part of the tick, the player attempts to attack the enemy. There is an initial check during which the simulator checks whether the player makes a hit and if they do, calculates the damage done to the enemy.

The chance of hitting the enemy at all depends on the player's attack and the enemy's defence stat and is calculated using the formulas below:

| Stat Comparison                      | Formula                                                               |
|--------------------------------------|-----------------------------------------------------------------------|
| $`effectiveAttack \gt enemyDefence`$ | $`attackChance = 1 - \dfrac{enemyDefence}{2 \times effectiveAttack}`$ |
| $`effectiveAttack \le enemyDefence`$ | $`attackChance = \dfrac{effectiveAttack}{2\times enemyDefence}`$      |

Where:

$`effectiveAttack = playerAttack/playerMagic/playerRanged + weaponAttackBonus`$

(whichever attack stat matches the combat style in use)

This formula produces a chance between 0 and 1, which is then clamped in practice to 15%–95% for the player in dungeons (10%–95% for enemies, and for the player in boss fights). It works on a fractional scale so if your attack equals the enemy's defence, then your chance of making a hit is 50%. If attack is half the defence, then chance is 25% and so on.

The $`enemyDefence`$ stat is calculated using the defensive stat of the enemy relevant to the specific combat style (e.g. strength defence is used for strength combat styles, magic for magic, etc).

Assuming the player succeeds in hitting the enemy, the damage is a random number up to the maximum possible hit which depends on the attacking style as described below:

| Combat Style | Maximum Damage                                                                                              |
|--------------|-------------------------------------------------------------------------------------------------------------|
| Melee        | $`maximumDamage = 1 + (playerStrength + weaponStrengthBonus) \times \dfrac{weaponStrengthBonus + 64}{640}`$ |
| Ranged       | $`maximumDamage = 1 + (playerRanged + arrowStrengthBonus) \times \dfrac{arrowStrengthBonus + 64}{640}`$     |
| Magic        | $`maximumDamage = spellMaxHit`$                                                                             |

#### Phase 2: Enemy respawning / rewards

If, during phase 1, the player dealt enough damage to kill an enemy, then all drops associated with that enemy will be given out.

The XP dropped by enemies will be distributed providing 15% towards defence, 15% towards hitpoints with all remaining XP going to the relevant skill depending on combat style (i.e. Strength if using the strength combat style).

Additionally, the enemy of the same type that was killed will be respawned. I.e. After killing a goblin, another goblin will spawn.

More information about spawning is discussed in [Enemy Spawning](#enemy-spawning).

#### Phase 3: Enemy attack

During phase 3, the enemy will attempt to do damage to the player. Unlike the player however, the enemy has a fixed attack speed of 2.4s. This means that in some ticks, the enemy may not have an opportunity to do damage if the player's attack speed is faster than 2.4s.

If the enemy does get the opportunity to attack, then its method of doing damage is similar to the player. The chance of an enemy hitting is described using the formulas below (described [earlier](#phase-1-player-attack)).

| Stat Comparison                   | Formula                                                            |
|-----------------------------------|--------------------------------------------------------------------|
| $`enemyAttack \gt playerDefence`$ | $`attackChance = 1 - \dfrac{playerDefence}{2 \times enemyAttack}`$ |
| $`enemyAttack \le playerDefence`$ | $`attackChance = \dfrac{enemyAttack}{2\times playerDefence}`$      |

$`enemyAttack`$ is calculated as the attack level plus the attack bonus for the enemy and is otherwise known as the $`effectiveAttack`$.

#### Phase 4: Healing the player

Finally, once both the enemy and the player have had the chance to make hits, the player will heal themselves repeatedly by eating equipped food until the maximum allowed food has been eaten (300) or until there is less missing HP than the food would heal (e.g. the player will stop eating if they have 90/100 HP and the food has > 10 healing power). Additionally, if the enemy can kill the player in a single hit or the current HP is less than half the maximum, the player will also continue to eat.

Up to a maximum of 300 food can be consumed within a dungeon and the best equipped food (by healing) will be consumed first before lower tier food.

## Boss Fight Simulations

While similar to dungeons, bosses are simulated somewhat differently.

The most notable differences include the following:

- Boss fights conclude as soon as a boss or the player is defeated.
- Bosses return fixed rewards upon their death and do not distribute XP except using their flat rewards (these can be viewed in the bestiary or in {bosses_link}).
- Boss fights use a maximum number of frames which can be larger or smaller than the default 60.

Boss fights work by simulating a tick-by-tick combat loop followed by calculating the rewards.

### Tick-by-tick combat loop

The tick-by-tick combat loop functions identically to the dungeon simulation except that if the boss or player dies, the simulation immediately ends moving to the rewards phase. To see how this is simulated, check out the [explanation for dungeons](#tick-by-tick-combat-loop).

### Rewards phase

Rewards are received regardless of whether you defeat the boss. If you win, you'll receive all drops associated with the boss, however if you lose, you'll only receive 10% of all XP drops.

Generally, bosses are a good way to receive weapons that are better than what is typically available through smithing as well as unique pets.

If the boss was not defeated by the end of the maximum time, then it can still count as a win. This works by calculating your damage per second (DPS) along with your maximum HP and comparing whether you would outlive the boss given the boss's max HP. This does not factor in healing potential and so generally, if you don't defeat the boss by the end frame, this would typically result in a loss.

{combat_footer}
