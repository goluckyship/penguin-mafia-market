# @s here is the player who triggered the spawn (see attempt_spawn.mcfunction)
summon minecraft:piglin_brute ~-6 ~ ~-6 {CustomName:'{"text":"The Don","color":"gold","bold":true}',CustomNameVisible:1b,PersistenceRequired:1b,Tags:["is_don","penguin_mafia_mob"]}

execute as @e[tag=is_don,distance=..12,limit=1,sort=nearest] run attribute @s minecraft:generic.max_health base set 60
execute as @e[tag=is_don,distance=..12,limit=1,sort=nearest] run attribute @s minecraft:generic.attack_damage base set 9
execute as @e[tag=is_don,distance=..12,limit=1,sort=nearest] run attribute @s minecraft:generic.movement_speed base set 0.3
execute as @e[tag=is_don,distance=..12,limit=1,sort=nearest] run item replace entity @s armor.chest with minecraft:netherite_chestplate
execute as @e[tag=is_don,distance=..12,limit=1,sort=nearest] run item replace entity @s armor.head with minecraft:golden_helmet
execute as @e[tag=is_don,distance=..12,limit=1,sort=nearest] run data merge entity @s {DeathLootTable:"penguin_mafia:entities/don_death"}
execute as @e[tag=is_don,distance=..12,limit=1,sort=nearest] run effect give @s minecraft:glowing infinite 0 true

execute as @a[distance=..50] run advancement grant @s only penguin_mafia:meet_the_don
tellraw @a[distance=..50] [{"text":"\u26A0 ","color":"red"},{"text":"The Don has arrived. Run.","color":"dark_red","bold":true}]
