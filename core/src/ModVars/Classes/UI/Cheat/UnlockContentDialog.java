package ModVars.Classes.UI.Cheat;

import arc.Core;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.scene.ui.Button;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import braindustry.ui.ModStyles;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

public class UnlockContentDialog extends BaseDialog {
    private static int counter = 0;
    private static float allScale = 1f;
    private Table items;

    public UnlockContentDialog() {
        super("Unlock content dialog");
        addCloseButton();
        addCloseListener();
    }

    @Override
    public void addCloseButton() {
        this.setFillParent(true);
        this.keyDown((key) -> {
            if (key == KeyCode.escape || key == KeyCode.back) {
                Core.app.post(this::hide);
            }

        });
        this.cont.pane((t) -> {
            this.items = t.margin(10.0F);
        }).left();
        this.shown(this::setup);
        this.hidden(() -> {
        });
        super.addCloseButton();
    }

    void setup() {
        this.items.clearChildren();
        this.items.left();
        float bsize = 40.0F;
        counter = 0;
        Vars.content.each(c -> {
            if (c instanceof UnlockableContent) {

                UnlockableContent content = (UnlockableContent) c;
                boolean invalidateBlock = content instanceof Block && (((Block) content).buildVisibility != BuildVisibility.shown && ((Block) content).buildVisibility != BuildVisibility.campaignOnly);
                boolean invalidateUnitType = content instanceof UnitType && content.isHidden();
                if (invalidateBlock || invalidateBlock)
                    return;
                this.items.table(Tex.pane, (t) -> {
                    t.margin(4.0F).marginRight(8.0F).left();
                    t.image(content.uiIcon).size(24.0F).padRight(4.0F).padLeft(4.0F);
                    Button button = new Button(ModStyles.buttonColor);
                    t.label(() -> (!content.localizedName.equals(content.name) ? content.localizedName : Strings.capitalize(content.name))
                            .replace("   ", "_\t===\t_")
                            .replace("  ", "_\t==\t_")
                            .replace(" ", "\n")
                            .replace("_\t==\t_", "  ")
                            .replace("_\t===\t_", "   ")).left().width(90.0F * 2f);
                    button.clicked(() -> {
                        if (content.unlocked()) {
                            content.clearUnlock();
                        } else {
                            content.unlock();
                        }
                    });
                    t.add(button).size(bsize).update((b) -> {
                        b.setColor(content.unlocked() ? Pal.accent : Color.grays(0.5f));
//                        b.setColor(content.unlocked() ? Color.lime : Color.scarlet);
                    });
                    t.setHeight(48.0f);
                }).pad(2.0F).height(48.0f / Scl.scl()).left().fillX();
                counter++;
                int coln = Vars.mobile ? 2 : 3;
                if (counter % coln == 0) {
                    this.items.row();
                }
            }
        });
    }
}
