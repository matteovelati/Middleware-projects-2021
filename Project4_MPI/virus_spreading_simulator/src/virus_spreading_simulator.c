#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <mpi.h>
#include <time.h>
#include <math.h>
#include <sys/time.h>

// 86400 seconds in 1 day

#define SIMULATION_DAYS 100 //8640000   // 100 days
#define CONTACT_TIME 600                // 10 minutes
#define RECOVER_TIME 864000             // 10 days
#define IMMUNE_TIME 7776000             // 90 days (3 months)

#define SPACES "          "
#define COS_45 0.525321989

typedef struct individual_t
{
    int id;
    int contact_time;
    int is_infected;
    int infection_time;
    int is_immune;
    int immune_time;
    double position[2];
    int direction[2];
    int country_id;
} individual;

typedef struct country_t
{
    int id;
    int n_susceptible;
    int n_infected;
    int n_immune;
} country;

// Simulation parameters
long int n_individuals = 100000, i_individuals = 100, w_world = 20000, l_world = 10000, w_country = 20000, l_country = 5000,
        n_countries, individual_speed = 1, max_spreading_distance = 5, time_step = 300;


int susc_ind_each_process, inf_ind_each_process, susc_ind_rem, inf_ind_rem, tot_each_process, tot_process_zero;

// Process parameters
int my_rank, n_size;


int calculate_country_id_from_position(double, double);
int is_close_enough_to_be_infected(double, double, double, double);
void print_country_statistics(country *, int, int);
void initialize_population(individual *);
void update_population_parameters(individual *, int **, int);
void calculate_countries_statistics(individual *, country *);
void reset_countries_statistics(country *);
void print_separator();
void print_header();
void check_parameters_validity();

int main(int argc, char *argv[]){
    MPI_Init(NULL, NULL);
    MPI_Comm_rank(MPI_COMM_WORLD, &my_rank);
    MPI_Comm_size(MPI_COMM_WORLD, &n_size);

    check_parameters_validity();


    if (my_rank == 0) {
        printf("Welcome to 'Virus Spreading Simulator\n");
        printf("Initializing world...\n");
    }

    individual *population = NULL;
    country *countries = NULL;

    n_countries = (w_world / w_country) * (l_world / l_country);
    countries = (country *)malloc(sizeof(country) * n_countries);

    // Initialize countries statistics
    reset_countries_statistics(countries);

    srand(100 * my_rank + 1);

    // Initialize population
    susc_ind_each_process = (n_individuals - i_individuals) / n_size;
    inf_ind_each_process = i_individuals / n_size;
    susc_ind_rem = (n_individuals - i_individuals) % n_size;
    inf_ind_rem = i_individuals % n_size;

    tot_each_process = susc_ind_each_process + inf_ind_each_process;
    tot_process_zero = tot_each_process + susc_ind_rem + inf_ind_rem;


    if (my_rank == 0)
        population = (individual *) malloc(sizeof(individual) * tot_process_zero);
    else
        population = (individual *) malloc(sizeof(individual) * tot_each_process);
    initialize_population(population);


    int time_step_per_day = 86400 / time_step;


    int **infected_world_positions = (int **)malloc(w_world * sizeof(int *));
    for (int i = 0; i < w_world; ++i) {
        infected_world_positions[i] = (int *)malloc(l_world * sizeof(int));
        for (int j = 0; j < l_world; ++j) {
            infected_world_positions[i][j] = 0;
        }
    }

    MPI_Barrier(MPI_COMM_WORLD);
    if (my_rank == 0)
        printf("Starting simulation...\n");

    double start_time = MPI_Wtime();

    for(int day = 0; day < SIMULATION_DAYS; day ++){

        double start_day_time = MPI_Wtime();
        double bcast_time = 0;

        for(int step = 0; step < time_step_per_day; step ++){
            int process_infected_individuals = 0;
            double *infected_positions = NULL;


            // Calculate new positions and save infected positions
            for(int index = 0; index < tot_process_zero; index ++){
                if (my_rank != 0 && index > tot_each_process - 1){
                    break;
                }
                population[index].position[0] += population[index].direction[0] * time_step * individual_speed;
                population[index].position[1] += population[index].direction[1] * time_step * individual_speed;

                int outside_world = 0;
                // If outside the world go back and change direction
                if (population[index].position[0] > w_world - 1){
                    population[index].position[0] = w_world - (population[index].position[0] - (w_world - 1));
                    outside_world = 1;
                }
                else if (population[index].position[0] < 0){
                    population[index].position[0] = - population[index].position[0];
                    outside_world = 1;
                }
                if (population[index].position[1] > l_world - 1){
                    population[index].position[1] = l_world - (population[index].position[1] - (l_world - 1));
                    outside_world = 1;
                }
                else if (population[index].position[1] < 0){
                    population[index].position[1] = - population[index].position[1];
                    outside_world = 1;
                }
                // Change direction randomly
                int rand_direction = rand() % 4;
                if (outside_world == 1 || (rand() % 2) == 1){
                    switch (rand_direction) {
                        case 0: // 0
                            population[index].direction[0] = 1;
                            population[index].direction[1] = 0;
                            break;
                        case 1: // 90
                            population[index].direction[0] = 0;
                            population[index].direction[1] = 1;
                            break;
                        case 2: // 180
                            population[index].direction[0] = -1;
                            population[index].direction[1] = 0;
                            break;
                        default: // 270
                            population[index].direction[0] = 0;
                            population[index].direction[1] = -1;
                            break;
                    }
                }
                population[index].country_id = calculate_country_id_from_position(population[index].position[0],
                                                                                  population[index].position[1]);
                if (population[index].is_infected == 1){
                    process_infected_individuals ++;
                    if (infected_positions == NULL) {
                        infected_positions = (double *) malloc(sizeof(double) * process_infected_individuals * 2);
                    } else{
                        infected_positions = realloc(infected_positions, sizeof(double) * process_infected_individuals * 2);
                    }
                    infected_positions[(process_infected_individuals * 2) - 2] = population[index].position[0];
                    infected_positions[(process_infected_individuals * 2) - 1] = population[index].position[1];

                    infected_world_positions[(int)population[index].position[0]][(int)population[index].position[1]] = 1;

                }
            }

            // Exchange among all the process all the infected individuals positions
            int infected_positions_size = process_infected_individuals * 2;
            double *new_infected_positions = NULL;
            int new_infected_positions_size = 0;

            double start_bcast_time = MPI_Wtime();
            for (int sender_process = 0; sender_process < n_size; sender_process ++) {
                if (my_rank == sender_process){
                    // Send size of infected_positions
                    MPI_Bcast(&infected_positions_size, 1, MPI_INT, sender_process, MPI_COMM_WORLD);

                    // Send infected positions
                    if (infected_positions_size > 0)
                        MPI_Bcast(infected_positions, infected_positions_size, MPI_DOUBLE, sender_process, MPI_COMM_WORLD);
                }
                else{
                    int received_infected_positions_size = 0;
                    double *received_infected_positions = NULL;
                    // Receive size of infected_positions
                    MPI_Bcast(&received_infected_positions_size, 1, MPI_INT, sender_process, MPI_COMM_WORLD);

                    if (received_infected_positions_size > 0) {
                        received_infected_positions = (double *) malloc(
                                sizeof(double) * received_infected_positions_size);
                        // Receive infected positions
                        MPI_Bcast(received_infected_positions, received_infected_positions_size, MPI_DOUBLE,
                                  sender_process, MPI_COMM_WORLD);

                        for (int i = 0; i < received_infected_positions_size; i += 2) {
                            infected_world_positions[(int) received_infected_positions[i]][(int) received_infected_positions[
                                    i + 1]] = 1;
                        }

                        // Save infected positions
                        if (new_infected_positions == NULL) {
                            new_infected_positions = (double *) malloc(
                                    sizeof(double) * received_infected_positions_size);
                        } else {
                            new_infected_positions = realloc(new_infected_positions, sizeof(double) *
                                                                                     (new_infected_positions_size +
                                                                                      received_infected_positions_size));
                        }
                        for (int i = new_infected_positions_size;
                             i < new_infected_positions_size + received_infected_positions_size; i++) {
                            new_infected_positions[i] = received_infected_positions[i - new_infected_positions_size];
                        }
                        new_infected_positions_size += received_infected_positions_size;

                        free(received_infected_positions);
                    }
                }
            }
            bcast_time += MPI_Wtime() - start_bcast_time;

            // Calculate if an individual is in contact with an infected one and update all parameters
            update_population_parameters(population, infected_world_positions, process_infected_individuals + new_infected_positions_size/2);

            for (int i = 0; i < process_infected_individuals * 2; i += 2) {
                infected_world_positions[(int)infected_positions[i]][(int)infected_positions[i+1]] = 0;
            }
            for (int i = 0; i < new_infected_positions_size; i += 2) {
                infected_world_positions[(int)new_infected_positions[i]][(int)new_infected_positions[i+1]] = 0;
            }
            free(infected_positions);
            free(new_infected_positions);
        }

        // Calculate countries statistics
        calculate_countries_statistics(population, countries);

        // Exchange country statistics among processes
        if (my_rank == 0){
            print_header();
        }
        double start_tmp_time = MPI_Wtime();
        for (int i = 0; i < n_countries; i ++){
            int tot_sum = 0;
            MPI_Reduce(&(countries[i].n_susceptible), &tot_sum, 1, MPI_INT, MPI_SUM, 0, MPI_COMM_WORLD);
            if (my_rank == 0)
                countries[i].n_susceptible = tot_sum;
            MPI_Reduce(&(countries[i].n_infected), &tot_sum, 1, MPI_INT, MPI_SUM, 0, MPI_COMM_WORLD);
            if (my_rank == 0)
                countries[i].n_infected = tot_sum;
            MPI_Reduce(&(countries[i].n_immune), &tot_sum, 1, MPI_INT, MPI_SUM, 0, MPI_COMM_WORLD);
            if (my_rank == 0)
                countries[i].n_immune = tot_sum;
            if (my_rank == 0)
                print_country_statistics(countries, i, day);
        }
        double end_tmp_time = MPI_Wtime();

        /* DEBUG
        if (my_rank == 0) {
            printf("Country exchange time: %f\n", end_tmp_time - start_tmp_time);
            printf("Bcast exchange time: %f\n", bcast_time);
        }*/


        // Reset country statistics
        reset_countries_statistics(countries);

        double end_day_time = MPI_Wtime();

        /* DEBUG
        if (my_rank == 0)
            printf("Day execution time: %f\n\n", (end_day_time - start_day_time));
        */

    }

    double end_time = MPI_Wtime();

    if (my_rank == 0)
        printf("Total execution time: %f\n\n", (end_time - start_time));

    MPI_Barrier(MPI_COMM_WORLD);
    for (int i = 0; i < w_world; ++i) {
        free(infected_world_positions[i]);
    }
    free(infected_world_positions);
    free(population);
    free(countries);

    MPI_Finalize();

    return 0;
}

void check_parameters_validity(){
    if (w_world % w_country > 0 || l_world % l_country > 0){
        if (my_rank == 0)
            printf("ERROR - Invalid parameters\n");
        MPI_Finalize();
        exit(0);
    }
    if (time_step * individual_speed > w_world || time_step * individual_speed > l_world){
        if (my_rank == 0)
            printf("ERROR - Invalid parameters\n");
        MPI_Finalize();
        exit(0);
    }
}

void initialize_population(individual *population){
    for(int index = 0; index < tot_process_zero; index ++){
        if (my_rank != 0 && index > tot_each_process - 1){
            break;
        }
        int is_infected = 0;
        if (index < inf_ind_each_process){
            is_infected = 1;
        } else{
            if (my_rank == 0 && index < inf_ind_each_process + inf_ind_rem){
                is_infected = 1;
            } else{
                is_infected = 0;
            }
        }
        population[index].id = index;
        population[index].contact_time = 0;
        population[index].is_infected = is_infected;
        population[index].infection_time = 0;
        population[index].is_immune = 0;
        population[index].immune_time = 0;
        population[index].position[0] = rand() % w_world;
        population[index].position[1] = rand() % l_world;
        int rand_direction = rand() % 4;
        switch (rand_direction) {
            case 0: // 0
                population[index].direction[0] = 1;
                population[index].direction[1] = 0;
                break;
            case 1: // 90
                population[index].direction[0] = 0;
                population[index].direction[1] = 1;
                break;
            case 2: // 180
                population[index].direction[0] = -1;
                population[index].direction[1] = 0;
                break;
            default: // 270
                population[index].direction[0] = 0;
                population[index].direction[1] = -1;
                break;
        }
        population[index].country_id = calculate_country_id_from_position(population[index].position[0],
                                                                          population[index].position[1]);
    }
}

void update_population_parameters(individual *population, int **infected_world_positions, int tot_infected){
    for(int index = 0; index < tot_process_zero; index ++){
        if (my_rank != 0 && index > tot_each_process - 1){
            break;
        }
        if (population[index].is_infected == 0 && population[index].is_immune == 0){
            int has_been_in_contact = 0;
            if (tot_infected > 0) {
                int x_pop = (int) population[index].position[0];
                int y_pop = (int) population[index].position[1];
                for (int i = x_pop - max_spreading_distance; i < x_pop + max_spreading_distance + 1; ++i) {
                    if (has_been_in_contact == 1)
                        break;
                    for (int j = y_pop - max_spreading_distance; j < y_pop + max_spreading_distance + 1; ++j) {
                        if (i < w_world && i >= 0 && j < l_world && j >= 0) {
                            double x = (double) i;
                            double y = (double) j;
                            if (infected_world_positions[i][j] == 1 && is_close_enough_to_be_infected(
                                    population[index].position[0],
                                    population[index].position[1],
                                    x, y) == 1) {
                                has_been_in_contact = 1;
                                break;
                            }
                        }
                    }
                }
            }

            if (has_been_in_contact == 1){
                population[index].contact_time += time_step;
                if (population[index].contact_time > CONTACT_TIME){
                    population[index].contact_time = 0;
                    population[index].is_infected = 1;
                }
            }
            else{
                population[index].contact_time = 0;
            }
        } else{
            if (population[index].is_infected == 1){
                // Update infected parameters
                population[index].infection_time += time_step;
                if (population[index].infection_time > RECOVER_TIME){
                    population[index].is_infected = 0;
                    population[index].infection_time = 0;
                    population[index].is_immune = 1;
                }
            }
            else if (population[index].is_immune == 1){
                population[index].immune_time += time_step;
                if (population[index].immune_time > IMMUNE_TIME){
                    population[index].immune_time = 0;
                    population[index].is_immune = 0;
                }
            }
        }
    }
}

void calculate_countries_statistics(individual *population, country *countries){
    for(int index = 0; index < tot_process_zero; index ++){
        if (my_rank != 0 && index > tot_each_process - 1){
            break;
        }
        if (population[index].is_infected == 1){
            countries[population[index].country_id].n_infected ++;
        }
        else if (population[index].is_immune == 1){
            countries[population[index].country_id].n_immune ++;
        }
        else{
            countries[population[index].country_id].n_susceptible ++;
        }
    }
}

void reset_countries_statistics(country *countries){
    for (int i = 0; i < n_countries; i ++){
        countries[i].id = i;
        countries[i].n_susceptible = 0;
        countries[i].n_infected = 0;
        countries[i].n_immune = 0;
    }
}

int calculate_country_id_from_position(double x, double y){
    int x_value = (x / w_country) + 1;
    int y_value = (y / l_country) + 1;
    return ((w_world / w_country) * (y_value - 1)) + x_value - 1;
}

int is_close_enough_to_be_infected(double x1, double y1, double x2, double y2){
    double x_distance = fabs(x1 - x2);
    double y_distance = fabs(y1 - y2);
    if ((x_distance * x_distance + y_distance * y_distance) > (max_spreading_distance * max_spreading_distance)){
        return 0;
    }
    else{
        return 1;
    }
}

void print_country_statistics(country *countries, int country_id, int day){
    printf("|%20d%s|%20d%s|%20d%s|%20d%s|%20d%s|\n", day, SPACES, countries[country_id].id, SPACES, countries[country_id].n_susceptible, SPACES,
           countries[country_id].n_infected, SPACES, countries[country_id].n_immune, SPACES);
    print_separator();
}

void print_header(){
    printf("|%20s%s|%20s%s|%20s%s|%20s%s|%20s%s|\n", "DAY", SPACES, "COUNTRY ID", SPACES, "N_SUSCEPTIBLE", SPACES,
           "N_INFECTED", SPACES, "N_IMMUNE", SPACES);
    print_separator();
}

void print_separator(){
    for (int i = 0; i < 5; i ++){
        printf("*");
        for (int j = 0; j < 30; j ++){
            printf("-");
        }
    }
    printf("*\n");
}
