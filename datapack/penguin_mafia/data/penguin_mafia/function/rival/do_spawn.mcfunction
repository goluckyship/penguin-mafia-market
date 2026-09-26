summon minecraft:husk ~-5 ~ ~-5 {CustomName:'{"text":"Rival Gang Enforcer","color":"dark_red"}',CustomNameVisible:1b,PersistenceRequired:1b,Tags:["is_rival","penguin_mafia_mob"]}

execute as @e[tag=is_rival,tag=!geared,distance=..10,limit=1,sort=nearest] run item replace entity @s weapon.mainhand with minecraft:iron_sword
execute as @e[tag=is_rival,tag=!geared,distance=..10,limit=1,sort=nearest] run item replace entity @s armor.chest with minecraft:leather_chestplate
execute as @e[tag=is_rival,tag=!geared,distance=..10,limit=1,sort=nearest] run data merge entity @s {DeathLootTable:"penguin_mafia:entities/rival_death"}
execute as @e[tag=is_rival,tag=!geared,distance=..10,limit=1,sort=nearest] run tag @s add geared
