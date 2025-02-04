package frc.robot.subsystems;

import swervelib.SwerveController;
import swervelib.SwerveDrive;
import swervelib.SwerveDriveTest;
import swervelib.parser.SwerveParser;
import swervelib.telemetry.SwerveDriveTelemetry;
import swervelib.telemetry.SwerveDriveTelemetry.TelemetryVerbosity;

import java.io.File;
import java.util.function.DoubleSupplier;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.config.PIDConstants;
import com.pathplanner.lib.config.RobotConfig;
import com.pathplanner.lib.controllers.PPHolonomicDriveController;

import edu.wpi.first.epilogue.Logged;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Filesystem;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.wpilibj2.command.WaitCommand;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine.Config;
import frc.robot.LimelightHelpers;
import frc.robot.config.ConfigurationLoader;
import frc.robot.config.DriveBaseSubsystemConfig;

/**
 * The drive base subsystem for the robot.
 */
@Logged
public class DriveBaseSubsystem extends SubsystemBase {
    private boolean                  haveTarget             = true;
    private Translation2d            centerOfRotationMeters = new Translation2d();
    private static double            kDt                    = 0.02;

    private final TrapezoidProfile   xy_profile;
    private Translation2d            xy_speed               = new Translation2d();
    private Translation2d            xy_target              = new Translation2d();
    private TrapezoidProfile.State   xy_goal                = new TrapezoidProfile.State();
    private TrapezoidProfile.State   xy_setpoint            = new TrapezoidProfile.State();
    private double                   xy_last                = 0.0;
    private PIDController            xy_PID                 = new PIDController(6.0, 0.0, 0.0);

    private final TrapezoidProfile   r_profile              = new TrapezoidProfile(
            new TrapezoidProfile.Constraints(5.0, 0.75));                                                             // TODO:
                                                                                                                      // Maxrotational
                                                                                                                      // speed/accel?
    private double                   r_speed                = 0.0;
    private Rotation2d               r_target               = new Rotation2d();
    private TrapezoidProfile.State   r_goal                 = new TrapezoidProfile.State();
    private TrapezoidProfile.State   r_setpoint             = new TrapezoidProfile.State();
    private double                   r_last                 = 0.0;
    private PIDController            r_PID                  = new PIDController(6.0, 0.0, 0.0);

    private SwerveDrive              swerveDrive;
    private SwerveController         swerveController;
    private DriveBaseSubsystemConfig driveBaseSubsystemConfig;

    NetworkTable                     table                  = NetworkTableInstance.getDefault().getTable("limelight");
    NetworkTableEntry                tx                     = table.getEntry("tx");
    NetworkTableEntry                ty                     = table.getEntry("ty");
    NetworkTableEntry                ta                     = table.getEntry("ta");
    NetworkTableEntry                tid                    = table.getEntry("tid");
    NetworkTableEntry                tl                     = table.getEntry("tl");
    NetworkTableEntry                cl                     = table.getEntry("cl");
    NetworkTableEntry                botpose                = table.getEntry("botpose");

    RobotConfig                      robotConfig;

    //
    // Command to manually move the robot using joysticks field oriented
    //
    //
    class MoveManualCommandRobot extends Command {
        private final DoubleSupplier     x;
        private final DoubleSupplier     y;
        private final DoubleSupplier     r;
        private final DriveBaseSubsystem driveBase;

        // Constructor
        public MoveManualCommandRobot(DriveBaseSubsystem subsystem, DoubleSupplier new_x, DoubleSupplier new_y,
                DoubleSupplier new_r) {
            super();
            x         = new_x;
            y         = new_y;
            r         = new_r;
            driveBase = subsystem;
            addRequirements(driveBase);
        }

        // Called every time the scheduler runs while the command is scheduled.
        @Override
        public void execute() {
            super.execute();
            var new_x = -x.getAsDouble();
            var new_y = -y.getAsDouble();
            var new_r = -r.getAsDouble();
            driveBase.driveRobot(new_x, new_y, new_r);
        }

        // Called once the command ends or is interrupted.
        @Override
        public void end(boolean interrupted) {
            super.end(interrupted);
            driveBase.stop();
        }
    }

    /**
     * Constructor
     */
    public DriveBaseSubsystem() {
        try {
            // Load Configuration
            // TODO: this should probably be injected instead of located
            driveBaseSubsystemConfig = ConfigurationLoader.load("drivebasesubsystem.json",
                    DriveBaseSubsystemConfig.class);
            swerveDrive              = new SwerveParser(new File(Filesystem.getDeployDirectory(), "swerve"))
                    .createSwerveDrive(driveBaseSubsystemConfig.maximumSpeedInMeters());
            swerveController         = swerveDrive.swerveController;
            swerveController.thetaController.setTolerance(Math.PI / driveBaseSubsystemConfig.thetaControllerTolerance,
                    0.1);
            swerveController.thetaController.setPID(driveBaseSubsystemConfig.thetaControllerPidKp,
                    driveBaseSubsystemConfig.thetaControllerPidKi, driveBaseSubsystemConfig.thetaControllerPidKd);
            robotConfig = RobotConfig.fromGUISettings();
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwerveDriveTelemetry.verbosity = TelemetryVerbosity.HIGH;
        xy_PID.setTolerance(0.05, 0.05);
        xy_PID.setIntegratorRange(-0.04, 0.04);
        xy_PID.setSetpoint(0);

        r_PID.setTolerance(0.05, 0.05);
        r_PID.setIntegratorRange(-0.04, 0.04);
        r_PID.setSetpoint(0);

        xy_profile = new TrapezoidProfile(
                new TrapezoidProfile.Constraints(driveBaseSubsystemConfig.maximumSpeedInMeters(), 0.75)); // TODO: Max
                                                                                                          // linear
                                                                                                          // accel?
        swerveDrive.setMotorIdleMode(true);

        AutoBuilder.configure(this::getPose, // Robot pose supplier
                this::resetPose, // Method to reset odometry (will be called if your auto has a starting pose)
                this::getRobotRelativeSpeeds, // ChassisSpeeds supplier. MUST BE ROBOT RELATIVE
                (speeds, feedforwards) -> swerveDrive.drive(speeds, // Method that will drive the robot given ROBOT
                                                                    // RELATIVE ChassisSpeeds. Also optionally outputs
                                                                    // individual module feedforwards
                        swerveDrive.kinematics.toSwerveModuleStates(speeds), feedforwards.linearForces()),
                new PPHolonomicDriveController( // PPHolonomicController is the built in path following controller for
                                                // holonomic drive trains
                        new PIDConstants(5.0, 0.0, 0.0), // Translation PID constants
                        new PIDConstants(5.0, 0.0, 0.0) // Rotation PID constants
                ), robotConfig, // The robot configuration
                () -> {
                    // Boolean supplier that controls when the path will be mirrored for the red
                    // alliance
                    // This will flip the path being followed to the red side of the field.
                    // THE ORIGIN WILL REMAIN ON THE BLUE SIDE

                    var alliance = DriverStation.getAlliance();
                    if (alliance.isPresent()) {
                        return alliance.get() == DriverStation.Alliance.Red;
                    }
                    return false;
                }, this // Reference to this subsystem to set requirements
        );
    }

    /**
     * Called once per timeslice
     *
     * @return void
     */
    @Override
    public void periodic() {
        // This method will be called once per scheduler run
        Pose2d current_pose = swerveDrive.swerveDrivePoseEstimator.getEstimatedPosition();
        limelightPeriodic(current_pose.getRotation().getDegrees());
        SmartDashboard.putNumber("RobotX", current_pose.getX());
        SmartDashboard.putNumber("RobotY", current_pose.getY());
        SmartDashboard.putNumber("RobotRot", current_pose.getRotation().getDegrees());
    }

    /**
     * Called once per timeslice while simulating
     *
     * @return void
     */
    @Override
    public void simulationPeriodic() {
        // This method will be called once per scheduler run when in simulation

    }

    /**
     * Gets estimated pose from limelight if available
     *
     * @param degrees angle robot is facing
     * @return void
     */
    private void limelightPeriodic(double degrees) {
        // read values periodically
        double x      = tx.getDouble(0.0);
        double y      = ty.getDouble(0.0);
        double tempid = tid.getDouble(0.0);
        double area   = ta.getDouble(0.0);

        // post to smart dashboard periodically
        SmartDashboard.putNumber("LimelightX", x);
        SmartDashboard.putNumber("LimelightY", y);
        SmartDashboard.putNumber("LimelightTID", tempid);
        SmartDashboard.putNumber("LimelightArea", area);

        LimelightHelpers.SetRobotOrientation("limelight", degrees, 0.0, 0.0, 0.0, 0.0, 0);
        LimelightHelpers.PoseEstimate mt2 = LimelightHelpers.getBotPoseEstimate_wpiBlue_MegaTag2("limelight");
        if (mt2.tagCount != 0) {
            swerveDrive.addVisionMeasurement(mt2.pose, mt2.timestampSeconds);
        }
    }

    /**
     * Issue set speeds to swerve drive
     *
     * @return void
     */
    private void drive(boolean fieldRelative, boolean isOpenLoop) {
        swerveDrive.drive(xy_speed, r_speed, fieldRelative, isOpenLoop, centerOfRotationMeters);
    }

    /**
     * Drive the robot using field-oriented control
     *
     * @param x   the x meters per second to move
     * @param y   the y meters per second to move
     * @param rot the radians per second to move
     * @return void
     */
    private void driveField(double x, double y, double r) {
        xy_speed = new Translation2d(x, y);
        r_speed  = r;
        drive(true, false);
    }

    /**
     * Drive the robot using robot-oriented control
     *
     * @param x   the x meters per second to move
     * @param y   the y meters per second to move
     * @param rot the radians per second to move
     * @return void
     */
    private void driveRobot(double x, double y, double r) {
        xy_speed = new Translation2d(x, y);
        r_speed  = r;
        drive(false, false);
    }

    /**
     * Set X and Y speeds for swerve drive base on distance from target
     *
     * @param current_pose of the robot
     * @return boolean true if within deadband otherwise false
     */
    private boolean SetXYSpeedsFromTarget(Translation2d current_pose) {
        Double  x_err    = xy_target.getX() - current_pose.getX();
        Double  y_err    = xy_target.getY() - current_pose.getY();
        Double  xy_err   = Math.hypot(x_err, y_err);
        boolean at_xy    = xy_err < 0.01;
        Double  velocity = 0.0;

        if (!at_xy) {
            xy_setpoint = xy_profile.calculate(kDt, xy_setpoint, xy_goal);
            velocity    = xy_setpoint.velocity;                                                   // + xy_pid.calculate(
                                                                                                  // ( xy_last - xy_err
                                                                                                  // ) / kDt ); test the
                                                                                                  // rest first
            xy_speed    = new Translation2d(velocity * x_err / xy_err, velocity * y_err / xy_err);
        } else {
            xy_setpoint = new TrapezoidProfile.State();
            xy_speed    = new Translation2d();
        }
        return at_xy;
    }

    /**
     * Set R speed for swerve drive base on angle to target
     *
     * @param current_pose of the robot
     * @return boolean true if within deadband otherwise false
     */
    private boolean SetRSpeedFromTarget(Rotation2d r) {
        Double  r_err = MathUtil.angleModulus(r_target.getRadians() - r.getRadians());
        boolean at_r  = Math.abs(r_err) < 0.1;

        if (!at_r) {
            r_setpoint = r_profile.calculate(kDt, r_setpoint, r_goal);
            r_speed    = r_setpoint.velocity;                         // + r_pid.calculate( ( r_last - r_err ) / kDt );
                                                                      // test the rest first
        } else {
            r_setpoint = new TrapezoidProfile.State();
            r_speed    = 0.0;
        }
        return at_r;
    }

    /**
     * Drive towards target pose
     *
     * @return void
     */
    private void driveToTarget() {
        boolean at_xy, at_r;
        Pose2d  current_pose = getPose();

        at_xy = SetXYSpeedsFromTarget(current_pose.getTranslation());
        at_r  = SetRSpeedFromTarget(current_pose.getRotation());

        if (!at_xy || !at_r) {
            haveTarget = false;
        } else {
            haveTarget = true;
        }
        drive(true, false);
    }

    /**
     * Drive facing target pose
     *
     * @return void
     */
    private void driveFacingTarget(double x, double y) {
        Pose2d current_pose = getPose();

        xy_speed = new Translation2d(x, y);
        setTarget(
                new Rotation2d(
                        Math.atan2(xy_target.getY() - current_pose.getY(), xy_target.getX() - current_pose.getX())),
                current_pose.getRotation());
        if (SetRSpeedFromTarget(current_pose.getRotation())) {
            haveTarget = true;
        } else {
            haveTarget = false;
        }
        drive(true, false);
    }

    /**
     * Drive facing target pose
     *
     * @return void
     */
    private void driveAtAngle(double x, double y) {
        Pose2d current_pose = getPose();

        xy_speed = new Translation2d(x, y);

        if (SetRSpeedFromTarget(current_pose.getRotation())) {
            haveTarget = true;
        } else {
            haveTarget = false;
        }
        drive(true, false);
    }

    /**
     * Sets the target pose for the robot (usually at the start of a command)
     *
     * @param new_target for the robot
     * @return void
     */
    private void setTarget(Pose2d new_target, Pose2d current_pose) {
        setTarget(new_target.getTranslation(), current_pose.getTranslation());
        setTarget(new_target.getRotation(), current_pose.getRotation());
    }

    /**
     * Sets the target translation for the robot (usually at the start of a command)
     *
     * @param new_target for the robot
     * @return void
     */
    private void setTarget(Translation2d new_target, Translation2d current_pose) {
        xy_target   = new_target;
        xy_last     = Math.hypot(xy_target.getX() - current_pose.getX(), xy_target.getY() - current_pose.getY());
        xy_setpoint = new TrapezoidProfile.State(xy_last, xy_setpoint.velocity);
        xy_PID.reset();
        haveTarget = false;
    }

    /**
     * Sets the target rotation for the robot (usually at the start of a command)
     *
     * @param new_target for the robot
     * @return void
     */
    private void setTarget(Rotation2d new_target, Rotation2d current_pose) {
        r_target   = new_target;
        r_last     = MathUtil.angleModulus(r_target.getRadians() - current_pose.getRadians());
        r_setpoint = new TrapezoidProfile.State(r_last, r_speed);
        r_PID.reset();
        haveTarget = false;
    }

    /**
     * Stop the robot by setting chassis speeds to 0.0
     *
     * @return void
     */
    private void stop() {
        haveTarget  = true;
        xy_setpoint = new TrapezoidProfile.State();
        r_setpoint  = new TrapezoidProfile.State();
        driveField(0.0, 0.0, 0.0);
        swerveDrive.lockPose();
    }

    /**
     * Returns the latest pose of the robot from odometery
     *
     * @return current pose of robot
     */
    public Pose2d getPose() {
        return swerveDrive.getPose();
    }

    public void resetPose(Pose2d new_pose) {
        swerveDrive.resetOdometry(new_pose);
    }

    public ChassisSpeeds getRobotRelativeSpeeds() {
        return swerveDrive.getRobotVelocity();
    }

    /**
     * Sets the current pose of the robot from odometery (usually at the start of
     * auton)
     *
     * @param new_pose of the robot
     * @return void
     */
    public void setPose(Pose2d new_pose) {
        swerveDrive.swerveDrivePoseEstimator.resetPose(new_pose);
    }

    //
    // Command to stop the robot and put wheels in X formation
    //
    //
    class StopCommand extends Command {
        private final DriveBaseSubsystem driveBase;

        // Constructor
        public StopCommand(DriveBaseSubsystem subsystem) {
            super();
            driveBase = subsystem;
            addRequirements(driveBase);
        }

        // Called every time the scheduler runs while the command is scheduled.
        @Override
        public void execute() {
            super.execute();
            swerveDrive.lockPose();
        }
    }

    /**
     * @return a Command for manual control
     */
    public Command stopManual() {
        return new StopCommand(this).raceWith(new WaitCommand(1.0));
    }

    //
    // Command to manually move the robot using joysticks field oriented
    //
    //
    class MoveManualCommandField extends Command {
        private final DoubleSupplier     x;
        private final DoubleSupplier     y;
        private final DoubleSupplier     r;
        private final DriveBaseSubsystem driveBase;

        // Constructor
        public MoveManualCommandField(DriveBaseSubsystem subsystem, DoubleSupplier new_x, DoubleSupplier new_y,
                DoubleSupplier new_r) {
            super();
            x         = new_x;
            y         = new_y;
            r         = new_r;
            driveBase = subsystem;
            addRequirements(driveBase);
        }

        // Called every time the scheduler runs while the command is scheduled.
        @Override
        public void execute() {
            super.execute();
            var new_x = -x.getAsDouble();
            var new_y = -y.getAsDouble();
            var new_r = -r.getAsDouble();
            driveBase.driveField(new_x, new_y, new_r);
        }

        // Called once the command ends or is interrupted.
        @Override
        public void end(boolean interrupted) {
            super.end(interrupted);
            driveBase.stop();
        }
    }

    /**
     * @return a Command for manual control
     */
    public Command moveManual(DoubleSupplier new_x, DoubleSupplier new_y, DoubleSupplier new_rot) {
        return new MoveManualCommandField(this, new_x, new_y, new_rot);
    }

    //
    // Command to manually translate the robot using joystick while it faces an
    // angle
    //
    //
    class MoveAtAngle extends Command {
        private final DoubleSupplier     x;
        private final DoubleSupplier     y;
        private final Rotation2d         r;
        private final DriveBaseSubsystem driveBase;

        // Constructor
        public MoveAtAngle(DriveBaseSubsystem subsystem, DoubleSupplier new_x, DoubleSupplier new_y, Rotation2d new_r) {
            super();
            x         = new_x;
            y         = new_y;
            r         = new_r;
            driveBase = subsystem;
            addRequirements(driveBase);
        }

        // Called when the command is initially scheduled.
        @Override
        public void initialize() {
            super.initialize();
            driveBase.setTarget(r, getPose().getRotation());
        }

        // Called every time the scheduler runs while the command is scheduled.
        @Override
        public void execute() {
            super.execute();
            var new_x = -x.getAsDouble();
            var new_y = -y.getAsDouble();
            driveBase.driveAtAngle(new_x, new_y);
        }

        // Called once the command ends or is interrupted.
        @Override
        public void end(boolean interrupted) {
            super.end(interrupted);
            driveBase.stop();
        }
    }

    /**
     * @return a Command for manual control of position while facing a Pose2d on the
     *         field
     */
    public Command moveAtAngle(DoubleSupplier new_x, DoubleSupplier new_y, Rotation2d new_pose) {
        return new MoveAtAngle(this, new_x, new_y, new_pose);
    }

    //
    // Command to move the robot to a new pose
    //
    //
    private class MoveToCommand extends Command {
        private final Pose2d             targetPose;
        private final DriveBaseSubsystem driveBase;

        // Constructor
        public MoveToCommand(DriveBaseSubsystem new_driveBase, Pose2d new_pose) {
            super();
            driveBase  = new_driveBase;
            targetPose = new_pose;
            addRequirements(driveBase);
        }

        // Called when the command is initially scheduled.
        @Override
        public void initialize() {
            super.initialize();
            driveBase.setTarget(targetPose, getPose());
        }

        // Called every time the scheduler runs while the command is scheduled.
        @Override
        public void execute() {
            super.execute();
            driveBase.driveToTarget();
        }

        // Called once the command ends or is interrupted.
        @Override
        public void end(boolean interrupted) {
            super.end(interrupted);
            driveBase.stop();
        }

        // Returns true when the command should end.
        @Override
        public boolean isFinished() {
            super.isFinished();
            return haveTarget;
        }
    }

    /**
     * @return a Command to go to a Pose2d
     */
    public Command moveTo(Pose2d new_pose) {
        return new MoveToCommand(this, new_pose);
    }

    //
    // Command to move the robot manually while facing a pose on the field
    //
    //
    private class MoveFacingCommand extends Command {
        private final DoubleSupplier     x;
        private final DoubleSupplier     y;
        private final Pose2d             targetPose;
        private final DriveBaseSubsystem driveBase;

        // Constructor
        public MoveFacingCommand(DriveBaseSubsystem subsystem, DoubleSupplier new_x, DoubleSupplier new_y,
                Pose2d new_pose) {
            super();
            x          = new_x;
            y          = new_y;
            targetPose = new_pose;
            driveBase  = subsystem;
            addRequirements(driveBase);
        }

        // Called when the command is initially scheduled.
        @Override
        public void initialize() {
            super.initialize();
            driveBase.setTarget(targetPose, getPose());
        }

        // Called every time the scheduler runs while the command is scheduled.
        @Override
        public void execute() {
            super.execute();
            var new_x = -x.getAsDouble();
            var new_y = -y.getAsDouble();
            driveBase.driveFacingTarget(new_x, new_y);
        }

        // Called once the command ends or is interrupted.
        @Override
        public void end(boolean interrupted) {
            super.end(interrupted);
            driveBase.stop();
        }
    }

    /**
     * @return a Command for manual control of position while facing a Pose2d on the
     *         field
     */
    public Command moveFacing(DoubleSupplier new_x, DoubleSupplier new_y, Pose2d new_pose) {
        return new MoveFacingCommand(this, new_x, new_y, new_pose);
    }

    /**
     * Return a Command to test the angle motors
     */
    public Command getAngleMotorTestCommand() {
        return SwerveDriveTest.generateSysIdCommand(
                SwerveDriveTest.setAngleSysIdRoutine(new Config(), this, swerveDrive), 3.0, 4.0, 4.0);
    }

    /**
     * Return a Command to test the drive motors
     */
    public Command getDriveMotorTestCommand() {
        return SwerveDriveTest.generateSysIdCommand(
                SwerveDriveTest.setDriveSysIdRoutine(new Config(), this, swerveDrive, 6.0, false), 3.0, 4.0, 4.0);
    }

    /**
     * Return a Command to test spinning the robot
     */
    // public Command getRobotAngleTestCommand()
    // {
    // return new DriveAngleSetCommand(new Rotation2d(0), this);
    // }
}
