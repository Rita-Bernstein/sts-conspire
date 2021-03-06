package conspire.monsters;

import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction.AttackEffect;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.animations.ShoutAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.StrengthPower;
import com.megacrit.cardcrawl.powers.VulnerablePower;
import com.megacrit.cardcrawl.powers.WeakPower;
import com.megacrit.cardcrawl.rewards.chests.AbstractChest;

import conspire.helpers.AscensionHelper;
import conspire.helpers.MovePicker;
import conspire.powers.HoldsTreasurePower;

public class MimicChest extends AbstractMonster {
    public static final String ID = "conspire:MimicChest";
    public static final String ENCOUNTER_NAME = ID;
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings(ID);
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;
    // location
    private static final float HB_X = 0.0f;
    private static final float HB_Y = 10.0f;
    private static final float HB_W = 260.0f;
    private static final float HB_H = 270.0f;
    // stats
    private static final int HP_MIN = 50;
    private static final int HP_MAX = 55;
    private static final int HP_FLOOR = 5;
    private static final int HP_FLOOR_A = 6;
    private static final int ATTACK_DMG = 13;
    private static final int ATTACK_DMG_A = 15;
    private static final float ATTACK_DMG_FLOOR = 0.25f;
    private static final int NOMNOM_DMG = 5;
    private static final int NOMNOM_DMG_A = 6;
    private static final int BLOCK_AMT = 6;
    private static final int BLOCK_AMT_A = 9;
    private static final float BLOCK_FLOOR = 0.25f;
    private int attackDmg;
    private int nomNomDmg;
    private int nomNomTimes;
    private int strUp = 3;
    private int weakDur = 2;
    private int defendBlock = 2;
    // moves
    private boolean screamed = false;
    private static final byte SCREAM = 1;
    private static final byte ROAR   = 2;
    private static final byte CHOMP  = 3;
    private static final byte NOMNOM = 4;
    private static final byte DEFEND = 5;

    /*
    Note: 512*512 chest image is placed at
        x = AbstractChest.CHEST_LOC_X*Settings.scale - 256.0f
        y = AbstractChest.CHEST_LOC_Y*Settings.scale - 256.0f
    adding relative image offset, the actual chest is at
        x = (AbstractChest.CHEST_LOC_X + 128 - 256) * Settings.scale
        y = (AbstractChest.CHEST_LOC_Y + 36 - 256) * Settings.scale
    Monster is placed at
        x = Settings.WIDTH * 0.75f + (offsetX - img.width/2) * Settings.scale
        y = AbstractDungeon.floorY + offsetY * Settings.scale
    Solving gives
        offsetX = AbstractChest.CHEST_LOC_X + 128.0f - 256.0f - 1920.0f*0.75f;
        offsetY = AbstractChest.CHEST_LOC_Y + 36.0f - 256.0f - AbstractDungeon.floorY/Settings.scale;
    */
    private static final float IMG_X = 128.f + 284.f/2.f;
    private static final float IMG_Y = 36.f;
    private static final float OFFSET_X = AbstractChest.CHEST_LOC_X/Settings.scale + IMG_X - 256.0f - 1920.0f*0.75f;
    private static final float OFFSET_Y = AbstractChest.CHEST_LOC_Y/Settings.scale + IMG_Y - 256.0f - AbstractDungeon.floorY/Settings.scale;

    public MimicChest() {
        this(OFFSET_X, OFFSET_Y);
    }

    public MimicChest(float x, float y) {
        super(NAME, ID, HP_MAX, HB_X, HB_Y, HB_W, HB_H, null, x, y);
        this.loadAnimation("conspire/images/monsters/MimicChest/skeleton.atlas", "conspire/images/monsters/MimicChest/skeleton.json", 1.0f);
        AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
        e.setTime(e.getEndTime() * 0.4f);
        this.type = EnemyType.ELITE;
        int floor = AbstractDungeon.floorNum;
        if (floor > 45) { // can happen in endless mode
            floor = Math.max(20, (floor - 1) % 45 + 1);
        }
        int floorHP = (AscensionHelper.tougher(this.type) ? HP_FLOOR_A : HP_FLOOR) * floor;
        this.setHp(HP_MIN+ floorHP, HP_MAX + floorHP);
        // damage amounts
        this.attackDmg = (AscensionHelper.deadlier(this.type) ? ATTACK_DMG_A : ATTACK_DMG) + Math.round(ATTACK_DMG_FLOOR * floor);
        this.nomNomDmg = AscensionHelper.deadlier(this.type) ? NOMNOM_DMG_A : NOMNOM_DMG;
        this.nomNomTimes = (AbstractDungeon.actNum - 1) % 3 + 2;
        this.defendBlock = (AscensionHelper.harder(this.type) ? BLOCK_AMT_A : BLOCK_AMT) + Math.round(BLOCK_FLOOR * floor);
        this.damage.add(new DamageInfo(this, attackDmg));
        this.damage.add(new DamageInfo(this, nomNomDmg));
        this.damage.add(new DamageInfo(this, attackDmg / 2));
    }

    @Override
    public void usePreBattleAction() {
        AbstractDungeon.actionManager.addToBottom(new SFXAction("MAW_DEATH", 0.1f));
        AbstractDungeon.actionManager.addToBottom(new ShoutAction(this, DIALOG[0], 1.0f, 2.0f));
        AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(this, this, new HoldsTreasurePower(this)));
    }

    @Override
    public void takeTurn() {
        switch (this.nextMove) {
            case SCREAM: {
                AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(AbstractDungeon.player, this, new WeakPower(AbstractDungeon.player, this.weakDur, true), this.weakDur));
                AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(AbstractDungeon.player, this, new VulnerablePower(AbstractDungeon.player, AbstractDungeon.actNum, true), AbstractDungeon.actNum));
                this.screamed = true;
                break;
            }
            case ROAR: {
                AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(this, this, new StrengthPower(this, this.strUp), this.strUp));
                break;
            }
            case CHOMP: {
                AbstractDungeon.actionManager.addToBottom(new AnimateSlowAttackAction(this));
                AbstractDungeon.actionManager.addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(0), AttackEffect.BLUNT_HEAVY));
                break;
            }
            case NOMNOM: {
                AbstractDungeon.actionManager.addToBottom(new AnimateSlowAttackAction(this));
                for (int i = 0 ; i < this.nomNomTimes ; ++i) {
                    AbstractDungeon.actionManager.addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(1), AttackEffect.BLUNT_LIGHT));
                }
                break;
            }
            case DEFEND: {
                AbstractDungeon.actionManager.addToBottom(new AnimateSlowAttackAction(this));
                AbstractDungeon.actionManager.addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(2), AttackEffect.BLUNT_LIGHT));
                AbstractDungeon.actionManager.addToBottom(new GainBlockAction(this, this, this.defendBlock));
                break;
            }
        }
        AbstractDungeon.actionManager.addToBottom(new RollMoveAction(this));
    }

    @Override
    protected void getMove(int num) {
        if (!screamed) {
            this.setMove(SCREAM, AbstractMonster.Intent.STRONG_DEBUFF);
            return;
        }
        MovePicker moves = new MovePicker();
        if (!this.lastMove(ROAR) && !this.lastMove(SCREAM) && !this.lastMove(DEFEND)) moves.add(ROAR, Intent.BUFF, 1.0f);
        if (!this.lastMove(CHOMP)) moves.add(CHOMP, Intent.ATTACK, this.damage.get(0).base, 1.0f);
        if (!this.lastTwoMoves(NOMNOM)) moves.add(NOMNOM, Intent.ATTACK, this.damage.get(1).base, this.nomNomTimes, true, 1.0f);
        if (!this.lastMove(DEFEND) && !this.lastMove(SCREAM)) moves.add(DEFEND, Intent.ATTACK_DEFEND, this.damage.get(2).base, 1.0f);
        moves.pickRandomMove(this);
    }
}