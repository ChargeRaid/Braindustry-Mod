const armor = extendContent(UnitType, "armor", {});
/*T1 unit*/
armor.constructor = () => {
const unit = extend(UnitEntity, {
})
return unit
}
armor.abilities.add(new ForceFieldAbility(30, 5, 100, 1200));

const shield = extendContent(UnitType, "shield", {});
/*T2 unit*/
shield.constructor = () => {
const unit = extend(UnitEntity, {
})
return unit
}
shield.abilities.add(new ForceFieldAbility(50, 6, 150, 1200));

const chestplate = extendContent(UnitType, "chestplate", {});
/*T3 unit*/
chestplate.constructor = () => {
const unit = extend(UnitEntity, {
})
return unit
}
chestplate.abilities.add(new ForceFieldAbility(70, 7, 220, 1200));

const chainmail = extendContent(UnitType, "chainmail", {});
/*T4 unit with heal field*/
chainmail.constructor = () => {
const unit = extend(UnitEntity, {
})
return unit
}
chainmail.abilities.add(new ForceFieldAbility(100, 7, 220, 1200), new RepairFieldAbility(130, 60, 140));

const broadsword = extendContent(UnitType, "broadsword", {});
/*T5 unit with heal field*/
broadsword.constructor = () => {
const unit = extend(UnitEntity, {
})
return unit
}
broadsword.abilities.add(new ForceFieldAbility(145, 7, 220, 1200), new RepairFieldAbility(210, 80, 210));
