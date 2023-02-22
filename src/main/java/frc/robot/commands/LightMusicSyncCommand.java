package frc.robot.commands;

import frc.robot.subsystems.LightsSubsystem;
import frc.robot.subsystems.LightsSubsystem.LEDSegment;
import edu.wpi.first.wpilibj2.command.CommandBase;
import java.util.ArrayList;
import java.util.List;
import edu.wpi.first.wpilibj.Timer;

public class LightMusicSyncCommand extends CommandBase{

    private int index = 0;
    private LightsSubsystem lightsSubsystem;
    private final Timer m_musicTimer = new Timer();
    private List<Boolean> isLedOn;
//Get & Index
   public LightMusicSyncCommand(
    List<Boolean> isLedOn,
    LightsSubsystem lightsSubsystem
    ) {
        addRequirements(lightsSubsystem);
        this.lightsSubsystem = lightsSubsystem;
        this.isLedOn = isLedOn;
    }

    //    public void ledStatus() {
    //         List<Boolean> isLedOn  = new ArrayList<>();
    //         LEDSegment.MainStrip.disableLEDs();
    //         LEDSegment.MainStrip.setColor(LightsSubsystem.orange);
    //     }

    @Override
    public void initialize() {
        m_musicTimer.reset();
        m_musicTimer.start();
        LEDSegment.MainStrip.setColor(LightsSubsystem.orange);

        
    }

    @Override
    public void execute() {
        if(isLedOn.get(index))
            

        if(m_musicTimer.hasElapsed(30)) LEDSegment.MainStrip.setColor(LightsSubsystem.red);
        if (m_musicTimer.hasElapsed(35)) LEDSegment.MainStrip.setColor(LightsSubsystem.orange);
        if (m_musicTimer.hasElapsed(120)) LEDSegment.MainStrip.disableLEDs();
        index++;
    }

    @Override
    public void end(boolean interrupted) {
        m_musicTimer.stop();
    }
}

// Set color - > Wait time -> Change color -> Wait time -> Change color -> Wait time -> Turn off.
