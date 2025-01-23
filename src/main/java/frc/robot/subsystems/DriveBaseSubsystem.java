// RobotBuilder Version: 6.1
//
// This file was generated by RobotBuilder. It contains sections of
// code that are automatically generated and assigned by robotbuilder.
// These sections will be updated in the future when you export to
// Java from RobotBuilder. Do not put any code or make any change in
// the blocks indicating autogenerated code or it will be lost on an
// update. Deleting the comments indicating the section will prevent
// it from being updated in the future.

// ROBOTBUILDER TYPE: Subsystem.

package frc.robot.subsystems;

import swervelib.SwerveController;
import swervelib.SwerveDrive;
import swervelib.SwerveDriveTest;
import swervelib.parser.SwerveParser;
import swervelib.telemetry.SwerveDriveTelemetry;
import swervelib.telemetry.SwerveDriveTelemetry.TelemetryVerbosity;

import java.io.File;

import edu.wpi.first.epilogue.Logged;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.Filesystem;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine.Config;
import frc.robot.config.ConfigurationLoader;
import frc.robot.config.DriveBaseSubsystemConfig;

// BEGIN AUTOGENERATED CODE, SOURCE=ROBOTBUILDER ID=IMPORTS

    // END AUTOGENERATED CODE, SOURCE=ROBOTBUILDER ID=IMPORTS

/**
 * The drive base subsystem for the robot.
 */
@Logged
public class DriveBaseSubsystem extends SubsystemBase {
    private SwerveDrive swerveDrive;
    private SwerveController swerveController;
    private DriveBaseSubsystemConfig driveBaseSubsystemConfig;
    // BEGIN AUTOGENERATED CODE, SOURCE=ROBOTBUILDER ID=CONSTANTS

    // END AUTOGENERATED CODE, SOURCE=ROBOTBUILDER ID=CONSTANTS

    // BEGIN AUTOGENERATED CODE, SOURCE=ROBOTBUILDER ID=DECLARATIONS

    // END AUTOGENERATED CODE, SOURCE=ROBOTBUILDER ID=DECLARATIONS

    /**
    *
    */
    public DriveBaseSubsystem() {
        try {
            // Load Configuration
            // TODO: this should probably be injected instead of located
            driveBaseSubsystemConfig = ConfigurationLoader.load("drivebasesubsystem.json",
                    DriveBaseSubsystemConfig.class);
            swerveDrive = new SwerveParser(new File(Filesystem.getDeployDirectory(), "swerve"))
                    .createSwerveDrive(driveBaseSubsystemConfig.maximumSpeedInMeters());
            swerveController = swerveDrive.swerveController;
            swerveController.thetaController.setTolerance(Math.PI / driveBaseSubsystemConfig.thetaControllerTolerance,
                    0.1);
            swerveController.thetaController.setPID(driveBaseSubsystemConfig.thetaControllerPidKp,
                    driveBaseSubsystemConfig.thetaControllerPidKi, driveBaseSubsystemConfig.thetaControllerPidKd);
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwerveDriveTelemetry.verbosity = TelemetryVerbosity.HIGH;
        // BEGIN AUTOGENERATED CODE, SOURCE=ROBOTBUILDER ID=CONSTRUCTORS

    // END AUTOGENERATED CODE, SOURCE=ROBOTBUILDER ID=CONSTRUCTORS
    }

    @Override
    public void periodic() {
        // This method will be called once per scheduler run
        Pose2d current_pose = swerveDrive.swerveDrivePoseEstimator.getEstimatedPosition();
        SmartDashboard.putNumber("RobotX", current_pose.getX());
        SmartDashboard.putNumber("RobotY", current_pose.getY());
        SmartDashboard.putNumber("RobotZ", current_pose.getRotation().getDegrees());
    }

    @Override
    public void simulationPeriodic() {
        // This method will be called once per scheduler run when in simulation

    }

    /**
     * Drive the robot using field-oriented control
     *
     * @param x   the x meters per second to move
     *
     * @param y   the y meters per second to move
     *
     * @param rot the radiants per second to move
     *
     * @return void
     */
    public void drive(double x, double y, double rot) {
        var chassisSpeeds = new ChassisSpeeds(x, y, rot);
        swerveDrive.driveFieldOriented(chassisSpeeds);
    }

    /**
     * Stop the robot by setting chassis speeds to 0
     */
    public void stop() {
        var chassisSpeeds = new ChassisSpeeds(0, 0, 0);
        swerveDrive.driveFieldOriented(chassisSpeeds);
    }
    /**
     * Return a Command to test the angle motors
     */
    public Command getAngleMotorTestCommand()
    {
        return SwerveDriveTest.generateSysIdCommand( SwerveDriveTest.setAngleSysIdRoutine(new Config(), this, swerveDrive), 3.0, 4.0, 4.0 );
    }
    /**
     * Return a Command to test the drive motors
     */
    public Command getDriveMotorTestCommand()
    {
        return SwerveDriveTest.generateSysIdCommand( SwerveDriveTest.setDriveSysIdRoutine(new Config(), this, swerveDrive, 6.0, false ), 3.0, 4.0, 4.0 );
    }
}
