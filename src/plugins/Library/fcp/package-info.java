/**
 * The FCP interface classes
 * <ul> <b>FCPRequestHandler</b> takes FCP requests and executed the corresponding methods </ul>
 * <ul> <b>FCPExposedMethods</b> the methods which can be executed by other plugins & using FCP </ul>
 * <ul> <b>ParameterTypes</b> enum of types which can be used for parameters and returns </ul>
 * <ul> <b>RemoteLibrary</b> not for use in Library but for plugins which want to conect to it </ul>
 * 
 * <h3> Connecting other plugin to Library</h3>
 * Just copy RemoteLibrary and ParameterTypes into your plugin, create an
 * instance of RemoteLibrary and call the methods
 * 
 * <h3> Adding methods </h3>
 * Add method to both Remote Library and FCPExposedMethods following their
 * instructions, making sure you only use Types in ParameterTypes.
 *
 * @author MikeB
 */
package plugins.Library.fcp;

