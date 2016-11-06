package unegdevelop.paintfragments;


import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import io.socket.emitter.Emitter;




// Nombres de las Clases en Mayusculas la Primera Palabra (igual con las Interfaces)
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class AudioStream
{

    //Nombres de las Constantes TOTALMENTE_EN_MAYUSCULAS
    // y antecedido por "final static", puede ser public o private o protected

    // Las Variables y las constantes deben estar Agrupadas por un lado todas los constantes
    // y por otro lado todas las variables, sin mezclas
    private final static int      SAMPLE_RATE = 44100;
    private final static int      STREAMING_TYPE = AudioManager.STREAM_MUSIC;
    private final static int      CHANNEL_CONFIG_RECORDER = AudioFormat.CHANNEL_IN_DEFAULT;
    private final static int      CHANNEL_CONFIG_PLAYER = AudioFormat.CHANNEL_OUT_MONO;
    private final static int      ENCODE_AUDIO_TYPE = AudioFormat.ENCODING_PCM_16BIT;
    private final static double   NIVEL_DE_AMPLITUD_MINIMO = 32F;
    private static final int      AMPLITUD_BYTES = 2800;

    //Nombres de las Variables y Objetos instanciados,
    //Comienzan en miniscula y las siguientes palabras, la 1era letra en mayuscula
    //NOTA: Los Nombres de las variables tienen que ser totalmente autodescriptivos
    //      es decir, tienen que hacer referencia total al valor que guardan

    private static int         bufferSize;
    private static boolean     recording = false;
    private static Thread      hiloGrabacion;
    private static AudioRecord grabador;
    private static AudioTrack  reproductor;
    private static MediaCodec codificador;
    private static MediaCodec  decodificador;

    private  static Emitter.Listener audioListener = new Emitter.Listener()
    {
        @Override
        public void call(Object... args)
        {
            byte[] data = (byte[]) args[0];
            try
            {
                addAudio(Utils.decompress(data));
            }
            catch (Throwable e)
            {
                e.printStackTrace();
            }
        }
    };

    private static Runnable runnableGrabar = new Runnable()
    {
        @Override
        public void run()
        {
            try
            {
                grabar();
            }
            catch (Throwable e)
            {
                e.printStackTrace();
            }
        }
    };

    public static void startRecording()
    {
        hiloGrabacion = new Thread(runnableGrabar);
        recording = true;
        hiloGrabacion.start();
    }

    public static  void stopRecording()
    {
        grabador.stop();
        recording = false;
        hiloGrabacion = null;
        grabador = null;
    }

    public static void startReceiver() throws Throwable
    {
        if(reproductor == null)
        {
            crearReproductor();
            Servidor.anadirEventoRecibidoAlSocket("get_audio",audioListener);
        }

    }

    public static void stopReceiver()
    {
        if(reproductor != null)
        {
            reproductor.stop();
            reproductor = null;
            Servidor.eliminarEvento("get_audio");
        }

    }

    public static boolean isRecording()
    {
        return recording;
    }

    private static void crearCodificador() throws IOException
    {
        codificador = MediaCodec.createEncoderByType("audio/mp4a-latm");
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1024);//AAC-HE 64kbps
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE);
        codificador.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private static void crearDecodificador() throws IOException
    {
        decodificador = MediaCodec.createDecoderByType("audio/mp4a-latm");
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1024);//AAC-HE 64kbps
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE);
        decodificador.configure(format, null, null, 0);
    }

    private static void crearGrabador() throws Throwable
    {
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_RECORDER, ENCODE_AUDIO_TYPE);

        grabador = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG_RECORDER,
                ENCODE_AUDIO_TYPE,
                bufferSize);

    }

    private static void crearReproductor() throws Throwable
    {
        bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_PLAYER, ENCODE_AUDIO_TYPE);
        reproductor = new AudioTrack(STREAMING_TYPE,
                SAMPLE_RATE,
                CHANNEL_CONFIG_PLAYER,
                ENCODE_AUDIO_TYPE,
                bufferSize,
                AudioTrack.MODE_STREAM
        );

    }

    private static void codificar(byte[] audioBuffer,int bufferSize) throws JSONException
    {

        ByteBuffer[] inputBuffers;
        ByteBuffer[] outputBuffers;

        ByteBuffer inputBuffer;
        ByteBuffer outputBuffer;

        MediaCodec.BufferInfo bufferInfo;
        int inputBufferIndex;
        int outputBufferIndex;

        byte[] outData;
        inputBuffers = codificador.getInputBuffers();
        outputBuffers = codificador.getOutputBuffers();
        inputBufferIndex = codificador.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0)
        {
            inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();

            inputBuffer.put(audioBuffer);

            codificador.queueInputBuffer(inputBufferIndex, 0, audioBuffer.length, 0, 0);
        }

        bufferInfo = new MediaCodec.BufferInfo();
        outputBufferIndex = codificador.dequeueOutputBuffer(bufferInfo, 0);



        while (outputBufferIndex >= 0)
        {
            outputBuffer = outputBuffers[outputBufferIndex];

            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

            outData = new byte[bufferInfo.size];
            outputBuffer.get(outData);

            //Aqui envio ..
            enviarStream(outData);

            codificador.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = codificador.dequeueOutputBuffer(bufferInfo, 0);

        }
    }

    private static void decodificar(byte[] audioBuffer)
    {

        ByteBuffer[] inputBuffers;
        ByteBuffer[] outputBuffers;

        ByteBuffer inputBuffer;
        ByteBuffer outputBuffer;

        MediaCodec.BufferInfo bufferInfo;
        int inputBufferIndex;
        int outputBufferIndex;

        byte[] outData;
        inputBuffers = decodificador.getInputBuffers();
        outputBuffers = decodificador.getOutputBuffers();
        inputBufferIndex = decodificador.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0)
        {
            System.out.println("INPUT:"+inputBufferIndex);
            inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();

            inputBuffer.put(audioBuffer);

            System.out.println(inputBuffer);

            decodificador.queueInputBuffer(inputBufferIndex, 0, audioBuffer.length, 0, 0);
        }

        bufferInfo = new MediaCodec.BufferInfo();

        outputBufferIndex = decodificador.dequeueOutputBuffer(bufferInfo, 0);
        System.out.println("OUTPUT: "+outputBufferIndex);


        while (outputBufferIndex >= 0)
        {
            outputBuffer = outputBuffers[outputBufferIndex];

            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

            outData = new byte[bufferInfo.size];
            outputBuffer.get(outData);
            System.out.println("Data Decodeada: "+ outData.length);

            //Aqui envio ..
            reproductor.write(outData,0,outData.length);

            decodificador.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = decodificador.dequeueOutputBuffer(bufferInfo, 0);

        }
    }

    private static void grabar() throws Throwable
    {
        //crearCodificador();
        crearGrabador();
        //codificador.start();
        grabador.startRecording();


        byte[] audioBuffer = new byte[bufferSize];
        byte[] compressAudio;
        int bufferResult;

        while (recording)
        {
            bufferResult = grabador.read(audioBuffer,0, bufferSize);

            compressAudio = Utils.compress(audioBuffer);
            if(isNoisy(bufferResult,audioBuffer) && compressAudio.length > AMPLITUD_BYTES)
            {
                enviarStream(compressAudio);
            }

            //if(isNoisy(bufferResult,audioBuffer))
            //  codificar(audioBuffer,bufferResult);
        }
    }

    private static boolean isNoisy(int bufferResult, byte[] audioBuffer)
    {
        double amplitudLevel = 0;
        double sumAmplitudLevel = 0;
        for (byte b : audioBuffer)
        {
            sumAmplitudLevel += Math.abs(b);
        }
        amplitudLevel = Math.abs(sumAmplitudLevel / bufferResult);
        if(amplitudLevel < NIVEL_DE_AMPLITUD_MINIMO)
            System.out.println("Amplitud:"+amplitudLevel);
        return (amplitudLevel > NIVEL_DE_AMPLITUD_MINIMO);
    }

    private static void enviarStream(byte[] audioBuffer) throws JSONException
    {
        Servidor.enviarEvento("send_audio",audioBuffer);
    }


    private static void addAudio(byte[] audio) throws JSONException, IOException
    {
        //Si el Reproductor no esta Reproduciendo lo inicia ..
        if (reproductor.getState() == AudioTrack.STATE_INITIALIZED)
        {
            reproductor.play();
            //crearDecodificador();
            //decodificador.start();

        }
        reproductor.write(audio,0,audio.length);

    }


}