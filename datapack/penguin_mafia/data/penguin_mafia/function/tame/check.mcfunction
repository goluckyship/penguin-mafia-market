# For every untamed penguin, if a player within 2 blocks is holding bread in
# their main hand, count it toward taming. After ~3 seconds of proximity, it tames.
execute as @e[type=minecraft:chicken,tag=is_penguin,tag=!is_tamed] at @s if entity @a[distance=..2,nbt={SelectedItem:{id:"minecraft:bread"}}] run function penguin_mafia:tame/progress
