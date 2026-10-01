package net.romeo.sightingsofaplayer.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;
import org.apache.logging.log4j.core.jmx.Server;
import org.joml.Vector3d;

import java.util.List;

public final class SOAPPlayerUtils {
    public final static double STANDARD_FOV = 110;

    public static boolean lookingToward(Player player, Vec3 targetDirection) {
        double fov = STANDARD_FOV;
        return lookingToward(player, targetDirection, Math.cos(Math.toRadians(fov)));
    }

    public static boolean lookingToward(Player player, Vec3 targetDirection, double threshold) {
        Vec3 lookAngle = player.getLookAngle().normalize();

        Vec3 normalizedTarget = targetDirection.normalize();

        double dotProduct = lookAngle.dot(normalizedTarget);

        return dotProduct >= threshold;
    }

    public static boolean lookingAt(Player player, Vec3 targetPos) {
        double fov = STANDARD_FOV;
        return lookingAt(player, targetPos, Math.cos(Math.toRadians(fov)));
    }

    public static boolean lookingAt(Player player, Vec3 targetPos, double threshold) {
        return lookingToward(player, targetPos.subtract(player.position()), threshold);
    }
}
