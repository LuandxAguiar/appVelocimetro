package com.devdx.velocimetrogiro;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private TextView velocidadeTextView;
    private TextView marchaTextView;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location localizacaoAnterior;
    private long tempoAnterior;
    private double currentMarcha = 0.0;

    private TextView distanceTextView;
    private TextView maxSpeedTextView;

    private double distanciaTotal = 0;
    private double velocidadeMaxima = 0;
    //teste loocalFused
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    // Peso para a leitura mais recente
    private static final double PESO_LEITURA_RECENTE = 0.8;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //referenciamento ao layout
        velocidadeTextView = findViewById(R.id.velocidadeTextView);
        marchaTextView = findViewById(R.id.marchaTextView);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        marchaTextView = findViewById(R.id.marchaTextView);
        distanceTextView = findViewById(R.id.distanceTextView);
        maxSpeedTextView = findViewById(R.id.maxSpeedTextView);

        mudarMarcha(3.0);

        fusedLocationClient =   LocationServices.getFusedLocationProviderClient(this);


        // Validação de GPS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            verificarGPSAtivado();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    private void verificarGPSAtivado() {
        // Verifique se o GPS está ativado
        if (!isGPSEnabled()) {
            exibirMensagemAtivarGPS();
        } else {
            iniciarLocalizacao();
        }
    }
    //verifica se o GPS esta ativado
    private boolean isGPSEnabled() {
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private void exibirMensagemAtivarGPS() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("O GPS está desativado. Por favor, ative-o para usar este aplicativo.")
                .setCancelable(false)
                .setPositiveButton("Ativar GPS", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Abre as configurações para permitir que o usuário ative o GPS
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    @SuppressLint("MissingPermission")
    private void iniciarLocalizacao() {
        // Configurar a solicitação de localização
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000) // Intervalo de tempo em milissegundos (1 segundo)
                .setFastestInterval(500); // Intervalo mais rápido para atualizações (500 milissegundos)

        // Configurar o callback de localização
        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        // Aqui você pode obter a leitura da localização, incluindo a velocidade
                        double novaVelocidade = calcularVelocidade(location);
                        suavizarLeituras(novaVelocidade);
                        verificarVelocidadeMinima();
                        atualizarVelocimetro(Math.abs(novaVelocidade));

                        if (localizacaoAnterior == null) {
                            localizacaoAnterior = location;
                            tempoAnterior = System.currentTimeMillis();
                            return;
                        }
                        distanciaTotal += location.distanceTo(localizacaoAnterior);
                        // Atualize a velocidade máxima
                        if (novaVelocidade > velocidadeMaxima) {
                            velocidadeMaxima = novaVelocidade;
                        }
                        atualizarVelocimetro(Math.abs(novaVelocidade));
                        atualizarDistanciaVelocidadeMaxima();
                    }
                }
            }
        };

        // Solicitar atualizações de localização
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void suavizarLeituras(double novaLeitura) {
        try {
            localizacaoAnterior = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            tempoAnterior = System.currentTimeMillis();
        } catch (SecurityException e) {
            Log.e("ERROR", "Erro ao obter localização anterior: " + e.getMessage());
        }
    }
    private void verificarVelocidadeMinima() {
    }

    private void atualizarVelocimetro(double velocidade) {
        velocidadeTextView.setText(String.format("%.1f", velocidade));
        marchaTextView.setText("Marcha: " + calcularMarcha(velocidade));
    }
    private double calcularMarcha(double velocidade) {
        if (velocidade == 0) {
            return 0; // Marcha neutra
        } else if (velocidade < 10) {
            return 1; // Primeira marcha
        } else if (velocidade < 30) {
            return 2; // Segunda marcha
        } else if (velocidade < 40) {
            return 3; // Outras marchas
        } else if (velocidade < 50) {
            return 4;
        }
        return 5;
    }

    private double calcularVelocidade(Location location) {
        try {
            if (localizacaoAnterior != null) {
                long tempoAtual = System.currentTimeMillis();
                long tempoDecorrido = tempoAtual - tempoAnterior;
                double distancia = location.distanceTo(localizacaoAnterior);

                // Imprimir mensagens de log para debug
                Log.d("DEBUG", "D: " + distancia);
                Log.d("DEBUG", "Tempo decorrido: " + tempoDecorrido);

                // Evita divisão por zero e converte para km/h
                if (tempoDecorrido > 0) {
                    double velocidadeMS = distancia / tempoDecorrido; // m/s
                    double velocidadeKMH = velocidadeMS * 3.6; // Conversão para km/h
                    return velocidadeKMH;
                }
            }
        } catch (Exception e) {
            Log.e("ERROR", "Erro ao calcular velocidade: " + e.getMessage());
        }
        return 0;
    }




    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);

            distanceTextView.setText(String.format("%.2f km", distanciaTotal));
            maxSpeedTextView.setText(String.format("%.2f km/h", velocidadeMaxima));
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarLocalizacao();
            } else {
            }
        }
    }
    //Stand by **_**
    private double calcularMediaPonderada(List<Location> leituras) {
        double somaPonderada = 0;
        double somaPesos = 0;

        for (int i = 0; i < leituras.size(); i++) {
            double peso = Math.pow(PESO_LEITURA_RECENTE, i);

            somaPonderada += peso * leituras.get(i).getSpeed();

            somaPesos += peso;
        }

        // Calcula a média ponderada final
        return somaPonderada / somaPesos;
    }
    //animação ao mudar de marcha
    private void mudarMarcha(double velocidade) {
        double novaMarcha = calcularMarcha(velocidade);

        if (novaMarcha != currentMarcha) {
            // Configurar a animação de piscar
            AlphaAnimation blinkAnimation = new AlphaAnimation(1, 0);
            blinkAnimation.setDuration(500); // Duração do piscar (500 ms)
            blinkAnimation.setRepeatCount(5); // Número de vezes que a animação será repetida
            marchaTextView.startAnimation(blinkAnimation);
            currentMarcha = novaMarcha;
        }
    }

    private void atualizarDistanciaVelocidadeMaxima() {
        // Atualiza os TextViews com a distância total e a velocidade máxima
        String distanciaTexto = String.format("Distância: %.2f km", distanciaTotal / 1000);
        distanceTextView.setText(distanciaTexto);

        String velocidadeMaximaTexto = String.format("Vel.Max: %.1f", velocidadeMaxima);
        maxSpeedTextView.setText(velocidadeMaximaTexto);
    }
}

